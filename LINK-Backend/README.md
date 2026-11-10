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

The backend returns safe, actionable statuses: invalid OpenAI credentials or model access return 502; rate limits or quota/billing errors return 429; provider timeouts return 504. For 429 responses, a validated OpenAI error code such as `rate_limit_exceeded` or `insufficient_quota` appears in the detail so you can distinguish temporary throughput limits from quota/spend issues. Credentials and raw provider responses are never included.

## Tests

Install development dependencies with `pip install -r requirements-dev.txt` and run `PYTHONPATH=. pytest -q`. Tests use mocked OpenAI responses and do not make paid API calls.
