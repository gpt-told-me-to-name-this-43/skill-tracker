import {
  closestCorners,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  TouchSensor,
  useDroppable,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import type { DragEndEvent } from "@dnd-kit/core";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { Link } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import { getLabels, getTasks, updateTaskStatus } from "../../api/tasksApi";
import { getUsers } from "../../api/usersApi";
import TaskCard from "../../components/TaskCard/TaskCard";
import { statusLabels } from "../../constants/taskStatus";
import type { Label, Person, TaskListItem, TaskStatus } from "../../types/task";

const kanbanStatuses: TaskStatus[] = ["todo", "in_progress", "review", "done"];

function SortableTaskCard({ task }: { task: TaskListItem }) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: String(task.id), data: { type: "task", status: task.status } });

  return (
    <TaskCard
      attributes={attributes}
      isDragging={isDragging}
      listeners={listeners ?? undefined}
      setNodeRef={setNodeRef}
      task={task}
      transform={transform}
      transition={transition}
    />
  );
}

function KanbanColumn({
  status,
  tasks,
}: {
  status: TaskStatus;
  tasks: TaskListItem[];
}) {
  const { setNodeRef, isOver } = useDroppable({
    id: status,
    data: { type: "column", status },
  });

  return (
    <article className={`kanban-column ${isOver ? "is-over" : ""}`} ref={setNodeRef}>
      <header className="kanban-column-header">
        <h2>{statusLabels[status]}</h2>
        <span>{tasks.length}</span>
      </header>

      <SortableContext items={tasks.map((task) => String(task.id))} strategy={verticalListSortingStrategy}>
        <section className="kanban-column-body">
          {tasks.length === 0 && <p className="kanban-empty">No tasks</p>}
          {tasks.map((task) => (
            <SortableTaskCard key={task.id} task={task} />
          ))}
        </section>
      </SortableContext>
    </article>
  );
}

function getTaskStatusFromDrop(event: DragEndEvent, tasks: TaskListItem[]) {
  const overId = event.over?.id ? String(event.over.id) : "";
  if (kanbanStatuses.includes(overId as TaskStatus)) {
    return overId as TaskStatus;
  }

  return tasks.find((task) => String(task.id) === overId)?.status ?? null;
}

export default function TasksPage() {
  const [tasks, setTasks] = useState<TaskListItem[]>([]);
  const [users, setUsers] = useState<Person[]>([]);
  const [labels, setLabels] = useState<Label[]>([]);
  const [assignee, setAssignee] = useState("all");
  const [difficulty, setDifficulty] = useState("all");
  const [label, setLabel] = useState("all");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [boardError, setBoardError] = useState("");

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 150, tolerance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  useEffect(() => {
    async function loadBoard() {
      try {
        const [tasksData, usersData, labelsData] = await Promise.all([
          getTasks(),
          getUsers(),
          getLabels(),
        ]);

        setTasks(tasksData);
        setUsers(usersData);
        setLabels(labelsData);
      } catch {
        setError("Не удалось загрузить доску.");
      } finally {
        setLoading(false);
      }
    }

    loadBoard();
  }, []);

  const difficulties = useMemo(
    () => [...new Set(tasks.map((task) => task.difficulty))].sort((first, second) => first - second),
    [tasks],
  );

  const filteredTasks = tasks.filter((task) => {
    const byAssignee = assignee === "all" || String(task.assignee?.id ?? "unassigned") === assignee;
    const byDifficulty = difficulty === "all" || task.difficulty === Number(difficulty);
    const byLabel = label === "all" || task.labels.some((item) => String(item.id) === label);

    return byAssignee && byDifficulty && byLabel;
  });

  async function handleDragEnd(event: DragEndEvent) {
    const taskId = Number(event.active.id);
    const task = tasks.find((item) => item.id === taskId);
    const nextStatus = getTaskStatusFromDrop(event, tasks);

    if (!task || !nextStatus || task.status === nextStatus) {
      return;
    }

    const previousTasks = tasks;
    setBoardError("");
    setTasks((currentTasks) => currentTasks.map((item) => (
      item.id === taskId ? { ...item, status: nextStatus } : item
    )));

    try {
      const updatedTask = await updateTaskStatus(taskId, nextStatus);
      if (!updatedTask) {
        throw new Error("Empty task response");
      }

      setTasks((currentTasks) => currentTasks.map((item) => (
        item.id === taskId ? { ...item, ...updatedTask } : item
      )));
    } catch {
      setTasks(previousTasks);
      setBoardError("Не удалось обновить статус задачи.");
    }
  }

  return (
    <main className="page-shell">
      <header className="page-header page-header-row">
        <section>
          <p>Project Management</p>
          <h1>Kanban board</h1>
        </section>
        <Link className="button-link" to="/tasks/new">Create Task</Link>
      </header>

      <section className="toolbar">
        <label htmlFor="assignee">Assignee</label>
        <select id="assignee" value={assignee} onChange={(event) => setAssignee(event.target.value)}>
          <option value="all">All</option>
          <option value="unassigned">Unassigned</option>
          {users.map((user) => (
            <option key={user.id} value={user.id}>{user.username}</option>
          ))}
        </select>

        <label htmlFor="difficulty">Difficulty</label>
        <select id="difficulty" value={difficulty} onChange={(event) => setDifficulty(event.target.value)}>
          <option value="all">All</option>
          {difficulties.map((item) => (
            <option key={item} value={item}>{item}/5</option>
          ))}
        </select>

        <label htmlFor="label">Label</label>
        <select id="label" value={label} onChange={(event) => setLabel(event.target.value)}>
          <option value="all">All</option>
          {labels.map((item) => (
            <option key={item.id} value={item.id}>{item.name}</option>
          ))}
        </select>
      </section>

      {loading && <section className="page-panel">Загрузка задач...</section>}
      {error && <section className="page-panel state-error">{error}</section>}
      {!loading && !error && tasks.length === 0 && (
        <section className="page-panel">Пока нет задач.</section>
      )}
      {!loading && !error && tasks.length > 0 && filteredTasks.length === 0 && (
        <section className="page-panel">Задачи не найдены.</section>
      )}
      {!loading && !error && tasks.length > 0 && (
        <DndContext collisionDetection={closestCorners} onDragEnd={handleDragEnd} sensors={sensors}>
          <section className="kanban-board" aria-label="Project kanban board">
            {kanbanStatuses.map((status) => (
              <KanbanColumn
                key={status}
                status={status}
                tasks={filteredTasks.filter((task) => task.status === status)}
              />
            ))}
          </section>
        </DndContext>
      )}

      {boardError && <section className="page-panel state-error">{boardError}</section>}
    </main>
  );
}
