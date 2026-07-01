async def test_create_and_get(client):
    resp = await client.post("/api/v1/skills", json={"name": "Python", "description": "Backend"})
    assert resp.status_code == 201
    created = resp.json()
    assert created["name"] == "Python"

    resp = await client.get(f"/api/v1/skills/{created['id']}")
    assert resp.status_code == 200
    assert resp.json()["name"] == "Python"


async def test_get_missing_returns_404(client):
    resp = await client.get("/api/v1/skills/999999")
    assert resp.status_code == 404


async def test_create_duplicate_returns_409(client):
    await client.post("/api/v1/skills", json={"name": "Docker"})
    resp = await client.post("/api/v1/skills", json={"name": "Docker"})
    assert resp.status_code == 409
