from pydantic import BaseModel


class GitHubSyncResult(BaseModel):
    created: int
    updated: int
    users_created: int
