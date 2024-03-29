from urllib import response
import pytest
import pytest_asyncio
import aiohttp

"""
For now it only works when the app is initialized
"""

BASE_URL = "http://localhost:8002/person"
ADMIN_UNAME = "myAdmin"
ADMIN_PASSWORD = "admin"


@pytest_asyncio.fixture
async def http_client():
    async with aiohttp.ClientSession() as session:
        yield session


@pytest.mark.asyncio
async def test_create_user(http_client):
    TEST_NAME = "Adam"
    TEST_PASSWORD = "password"
    TEST_MAIL = "email@gmail.com"

    async with http_client.post(
        BASE_URL + "/auth",
        json={"username": ADMIN_UNAME, "password": ADMIN_PASSWORD},
    ) as resp:
        assert resp.status == 200
        token = ( await resp.json() ).get("jwt-token")

    async with http_client.post(
        BASE_URL + "/User/user-admin",
        json={"username": TEST_NAME, "password": TEST_PASSWORD, "email": TEST_MAIL},
    ) as resp:
        assert resp.status == 403

    async with http_client.delete(
        BASE_URL + f"/User/\"role\"<>'admin'/user-admin",
        headers={"auth-token": token},
    ) as resp:
        assert resp.status == 200
        payload = await resp.json()

    async with http_client.post(
        BASE_URL + "/User/user-admin",
        json={"username": TEST_NAME, "password": TEST_PASSWORD, "email": TEST_MAIL, "role": "regular"},
        headers={"auth-token": token},
    ) as resp:
        assert resp.status == 201
        payload = await resp.json()

    async with http_client.get(
        BASE_URL + f"/User/\"username\"=='{TEST_NAME}'/user-admin",
        headers={"auth-token": token},
    ) as resp:
        assert resp.status == 200
        payload = await resp.json()
        user_id = payload[0].get("id")

    async with http_client.delete(
        BASE_URL + f"/User/\"id\"=='{user_id}'/user-admin",
        headers={"auth-token": token},
    ) as resp:
        assert resp.status == 200
        payload = await resp.json()

    async with http_client.post(
        BASE_URL + "/logout",
        headers={"auth-token": token},
    ) as resp:
        assert resp.status == 200
        payload = await resp.json()


@pytest.mark.asyncio
async def test_function_view(http_client):
    async with http_client.post(
        BASE_URL + "/auth",
        json={"username": ADMIN_UNAME, "password": ADMIN_PASSWORD},
    ) as resp:
        assert resp.status == 200
        token = ( await resp.json() ).get("jwt-token")

    async with http_client.get(
        BASE_URL + "/getPrimes",
        headers={"auth-token": token},
        params={"num": "lalalala"}
    ) as resp:
        assert resp.status == 400

    async with http_client.get(
        BASE_URL + "/getPrimes",
        headers={"auth-token": token},
        params={"num": 10}
    ) as resp:
        assert resp.status == 200
        payload = await resp.json()
        assert len(payload)


    async with http_client.post(
        BASE_URL + "/logout",
        headers={"auth-token": token},
    ) as resp:
        assert resp.status == 200
