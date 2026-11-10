from app.main import ChatRequest, VisionRequest, WebSearchRequest


def test_chat_defaults_to_adaptive_personality():
    assert ChatRequest(message="Oi").personality == "adaptive"


def test_web_search_query_validation():
    assert WebSearchRequest(query="pesquisa de tecnologia").query.startswith("pesquisa")


def test_vision_defaults_to_jpeg():
    payload = VisionRequest(image_base64="a" * 24)
    assert payload.mime_type == "image/jpeg"
