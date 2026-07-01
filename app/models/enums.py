import enum


class TaskStatus(str, enum.Enum):
    todo = "todo"
    in_progress = "in_progress"
    review = "review"
    done = "done"


class Difficulty(int, enum.Enum):
    trivial = 1
    easy = 2
    medium = 3
    hard = 4
    epic = 5
