# github-mcp-client

A Spring Boot REST app that uses **Spring AI** as an [MCP](https://modelcontextprotocol.io) client to
GitHub's **remote MCP server** (`https://api.githubcopilot.com/mcp/`). You ask a question in plain
English, OpenAI decides which GitHub MCP tools to call (`issue_read`, `list_pull_requests`,
`search_code`, ...), and the app answers from live GitHub data.

```
             POST /api/chat
 client ───────────────────────► ChatController
                                      │
                                      ▼
                                 ChatClient ── system prompt + chat memory
                                      │
                         ┌────────────┴─────────────┐
                         ▼                          ▼
                    OpenAI model ◄── tool calls ── SyncMcpToolCallbackProvider
                   (plans calls,                    │
                    writes answer)                  ▼
                                               MCP client (Streamable HTTP)
                                                    │  Authorization: Bearer <PAT>
                                                    │  X-MCP-Toolsets / X-MCP-Readonly
                                                    ▼
                                         GitHub remote MCP server
```

## Why two keys?

- **`GITHUB_PAT`**: the GitHub MCP server uses it to read GitHub data for you.
- **`OPENAI_API_KEY`**: MCP only exposes *tools*. Something has to turn "summarize the latest open
  PRs in spring-projects/spring-ai" into the right tool calls and then write the answer. That is
  the LLM's job.

## Tech stack

| | Version |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1 (`spring-ai-starter-mcp-client`, `spring-ai-starter-model-openai`) |
| MCP Java SDK | 2.0.0 (via Spring AI) |
| Build | Maven 3.9+ |

## Setup

1. **OpenAI API key**: <https://platform.openai.com/api-keys>
2. **GitHub token**, either of:
   - a fine-grained PAT with read access to *Contents*, *Issues*, *Pull requests* and *Metadata*
     on the repos you care about,
   - a classic PAT with `public_repo` (public repos only) or `repo`,
   - or, if you use the GitHub CLI, your existing login: `export GITHUB_PAT=$(gh auth token)`.

## Run

```bash
export OPENAI_API_KEY=sk-...
export GITHUB_PAT=github_pat_...      # or: export GITHUB_PAT=$(gh auth token)
mvn spring-boot:run
```

At startup the app connects to the GitHub MCP server and discovers its tools. If `GITHUB_PAT` is
missing, startup fails with `GitHub PAT is missing: set the GITHUB_PAT environment variable`. If
the token is invalid, startup fails with an MCP authorization error.

### Configuration

| Env var | Default | Purpose |
|---|---|---|
| `OPENAI_MODEL` | `gpt-5.4-mini` | OpenAI chat model (see [Model choice](#model-choice)) |
| `GITHUB_MCP_TOOLSETS` | `repos,issues,pull_requests` | [GitHub MCP toolsets](https://github.com/github/github-mcp-server#available-toolsets) to expose; `all` for everything |
| `GITHUB_MCP_READ_ONLY` | `true` | Only expose read-only tools. Set `false` to allow creating issues, comments, etc. |
| `SERVER_ADDRESS` | `127.0.0.1` | Network interface the HTTP server listens on (see [Security](#security)) |

Keeping the toolset list small matters: every exposed tool's schema is sent to the LLM on each
request, so fewer tools means fewer tokens and better tool selection.

## Security

The API has **no authentication**, and every request runs on *your* GitHub token and OpenAI
key. Anyone who can reach the port could read whatever your token can see (including private
repos) and spend your OpenAI credit. The app therefore listens on `127.0.0.1` only, so other
machines on your network cannot connect.

Before exposing it (e.g. `SERVER_ADDRESS=0.0.0.0` or deploying to a server):

- add authentication, such as Spring Security or an API-key header check;
- use a **fine-grained PAT** scoped to just the repos you need, not your personal `gh` login token;
- keep `GITHUB_MCP_READ_ONLY=true` unless you really need write tools.

## API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/tools` | Lists the GitHub MCP tools discovered at startup. Makes no LLM call, so it is a cheap check that MCP auth works. |
| `POST` | `/api/chat` | Body `{"message": "...", "conversationId": "optional"}`. Returns `{"conversationId": "...", "answer": "..."}`. |
| `GET` | `/actuator/health` | Health check |

Errors from OpenAI or the GitHub MCP server return **502** with an RFC 7807 problem-detail body.
A blank `message` returns **400**.

### Examples

```bash
# Which GitHub tools are available?
curl -s localhost:8080/api/tools | jq '.[].name'

# Ask a question
curl -s -X POST localhost:8080/api/chat -H 'Content-Type: application/json' \
  -d '{"message":"Summarize issue #1 in spring-projects/spring-ai"}' | jq

# Follow-up in the same conversation (reuse conversationId from the previous response)
curl -s -X POST localhost:8080/api/chat -H 'Content-Type: application/json' \
  -d '{"message":"Who closed it and how many files did it change?","conversationId":"<id>"}' | jq

# Lists and searches
curl -s -X POST localhost:8080/api/chat -H 'Content-Type: application/json' \
  -d '{"message":"List the 3 most recently created open pull requests in spring-projects/spring-ai"}' | jq
```

## How it works

| File | Role |
|---|---|
| `src/main/resources/application.yml` | Registers a Streamable HTTP MCP connection named `github` and the OpenAI model. |
| `config/GithubMcpProperties.java` | `github.mcp.*` settings (token, toolsets, read-only), validated at startup. |
| `config/McpGithubAuthConfig.java` | A `McpClientCustomizer<HttpClientStreamableHttpTransport.Builder>` that adds `Authorization: Bearer <PAT>`, `X-MCP-Toolsets` and `X-MCP-Readonly` headers to every request on the `github` connection. |
| `config/ChatClientConfig.java` | Builds the `ChatClient` with the system prompt, the MCP tools (`defaultTools(mcpTools)`) and a `MessageChatMemoryAdvisor`. Spring AI does **not** attach MCP tools to `ChatClient` automatically. |
| `web/ChatController.java` | `/api/chat` and `/api/tools`. Generates a `conversationId` when none is given. |
| `web/ApiExceptionHandler.java` | Maps `McpError`, `McpTransportException` and `OpenAIException` to 502. |

Chat memory is in-memory (it is lost on restart) and keeps the last 20 messages (Spring AI's `MessageWindowChatMemory` default)
per `conversationId`.

### Issues vs pull requests

GitHub issues and pull requests share **one number sequence**, so `#1234` could be either, and
users often say "issue" when they mean a PR or the other way round. The system prompt therefore
tells the model to:

1. look up any number with `issue_read` first, which works for both issues and PRs;
2. if the result is a pull request (`pull_request` field or `/pull/` URL), also call
   `pull_request_read`, because only that tool reports whether the PR was **merged**;
3. start the answer with "Pull request #N" or "Issue #N", and state the status exactly: a PR is
   open, merged (with date), or closed without merging; an issue is open or closed.

### Model choice

The default is `gpt-5.4-mini`. During testing, `gpt-4.1-mini` ignored the lookup steps above.
Asked about "PR #7093" (which is actually an issue), it called only `pull_request_read` and
replied that the number did not exist. It also never reported whether a PR was merged.
`gpt-5.4-mini` followed the steps every time (`issue_read` → `pull_request_read`) and labelled
all test cases correctly.

To see which tools the model calls, start the app with:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--logging.level.org.springframework.ai.model.tool=DEBUG
```

and look for `Executing tool call: <tool>` lines.

## Tests

```bash
mvn verify
```

`ChatControllerTest` is a `@WebMvcTest` with a mocked `ChatClient` and tool provider. It covers
the chat response, `conversationId` handling, validation (400), upstream errors (502) and the tool
listing. It needs neither API key nor network access.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `GitHub PAT is missing` at startup | Export `GITHUB_PAT`. |
| `McpHttpClientTransportAuthorizationException` at startup | Token invalid or expired. Check it with `curl -H "Authorization: Bearer $GITHUB_PAT" https://api.github.com/user`. |
| Model answers "not found" for a number that exists | You are on an older model; use the default `gpt-5.4-mini` or newer. |
| An expected tool is missing from `/api/tools` | Add its toolset to `GITHUB_MCP_TOOLSETS`, or set `GITHUB_MCP_READ_ONLY=false` for write tools. |
