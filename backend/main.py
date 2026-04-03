"""Episode REST API: list and detail endpoints."""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="Episodes API", version="1.0.0")


class Episode(BaseModel):
    """Episode entity exposed by the API."""

    id: int = Field(..., description="Stable episode identifier")
    title: str
    summary: str | None = None


# In-memory episode store (same data shape the CLI would surface)
_EPISODES: dict[int, Episode] = {
    1: Episode(id=1, title="Pilot", summary="Where it all begins."),
    2: Episode(id=2, title="The Second Chapter", summary="Things get complicated."),
    3: Episode(id=3, title="Finale Part One", summary="Setup for the end."),
}


@app.get("/episodes/", response_model=list[Episode])
def list_episodes() -> list[Episode]:
    """Return all episodes, ordered by id."""
    return sorted(_EPISODES.values(), key=lambda e: e.id)


@app.get("/episodes/{episode_id}", response_model=Episode)
def get_episode(episode_id: int) -> Episode:
    """Return a single episode by id."""
    ep = _EPISODES.get(episode_id)
    if ep is None:
        raise HTTPException(status_code=404, detail="Episode not found")
    return ep
