import pytest
import aiohttp

@pytest.fixture
async def http_client():
    async with aiohttp.ClientSession() as session:
        yield session


@pytest.mark.asyncio
async def test_app_creation(http_client):
    """
    Client tries to visit an endpoint
    """
    async with http_client.get('http://httpbin.org/get') as resp:
        assert resp.status == 200, "BAD STATUS!"

