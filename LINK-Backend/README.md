# LINK Backend 0.3.0

FastAPI backend for the LINK Android application. The service uses the OpenAI Responses API.

## Render configuration

- Root Directory: `LINK-Backend`
- Build Command: `pip install -r requirements.txt`
- Start Command: `uvicorn app.main:app --host 0.0.0.0 --port $PORT`
- Required environment variables: `OPENAI_API_KEY`, `LINK_ACCESS_TOKEN`; `OPENAI_MODEL` defaults to `gpt-5.5`.

Keep the values in Render's secret store. Do not put either credential in the Android application or client logs. After changing service variables, restart or redeploy the service so the process reads the new values.

## Diagnosing `/v1/chat`

`GET /health` confirms whether an OpenAI client was initialized, but does not validate the credential or model access. A successful chat test requires `Authorization: Bearer <LINK_ACCESS_TOKEN>` and a JSON body such as `{"message":"Responda apenas: LINK OK"}`.

The backend returns safe, actionable statuses: invalid OpenAI credentials or model access return 502; exhausted rate limits or credits return 429; provider timeouts return 504. Details never include credential values or the raw provider response.

## Tests

Install development dependencies with `pip install -r requirements-dev.txt` and run `PYTHONPATH=. pytest -q`. Tests use mocked OpenAI responses and do not make paid API calls.
