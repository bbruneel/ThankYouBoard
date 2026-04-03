"""Tests for GET /episodes/ and GET /episodes/{id}."""

from fastapi.testclient import TestClient

from main import app

client = TestClient(app)


def test_list_episodes_returns_all():
    r = client.get("/episodes/")
    assert r.status_code == 200
    data = r.json()
    assert len(data) == 3
    assert {e["id"] for e in data} == {1, 2, 3}
    assert data[0]["id"] < data[1]["id"] < data[2]["id"]


def test_get_episode_by_id():
    r = client.get("/episodes/1")
    assert r.status_code == 200
    assert r.json() == {
        "id": 1,
        "title": "Pilot",
        "summary": "Where it all begins.",
    }


def test_get_episode_missing_returns_404():
    r = client.get("/episodes/999")
    assert r.status_code == 404
