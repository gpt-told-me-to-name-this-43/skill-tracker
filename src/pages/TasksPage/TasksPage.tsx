import {
  closestCorners,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  pointerWithin,
  TouchSensor,
  useDroppable,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import type { CollisionDetection, DragEndEvent } from "@dnd-kit/core";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { Link } from "react-router-dom";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties, PointerEvent as ReactPointerEvent } from "react";
import { GITHUB_SYNC_COMPLETED_EVENT } from "../../api/integrationsApi";
import { getLabels, getTasks, updateTaskStatus } from "../../api/tasksApi";
import { getUsers } from "../../api/usersApi";
import TaskCard from "../../components/TaskCard/TaskCard";
import { statusLabels } from "../../constants/taskStatus";
import type { Label, Person, TaskListItem, TaskStatus } from "../../types/task";

const kanbanStatuses: TaskStatus[] = ["todo", "in_progress", "review", "done"];

const COLUMN_WIDTHS_STORAGE_KEY = "kanban-column-widths";
const COLUMN_MIN_WIDTH = 220;
const COLUMN_MAX_WIDTH = 640;

type ColumnWidths = Partial<Record<TaskStatus, number>>;

function loadStoredColumnWidths(): ColumnWidths {
  try {
    const raw = localStorage.getItem(COLUMN_WIDTHS_STORAGE_KEY);
    if (!raw) {
      return {};
    }

    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") {
      return {};
    }

    const widths: ColumnWidths = {};
    for (const status of kanbanStatuses) {
      const value = (parsed as Record<string, unknown>)[status];
      if (typeof value === "number" && Number.isFinite(value)) {
        widths[status] = Math.min(COLUMN_MAX_WIDTH, Math.max(COLUMN_MIN_WIDTH, value));
      }
    }
    return widths;
  } catch {
    return {};
  }
}

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
  width,
  onResize,
  onResetWidth,
}: {
  status: TaskStatus;
  tasks: TaskListItem[];
  width: number | undefined;
  onResize: (status: TaskStatus, width: number) => void;
  onResetWidth: (status: TaskStatus) => void;
}) {
  const { setNodeRef, isOver } = useDroppable({
    id: status,
    data: { type: "column", status },
  });
  const columnRef = useRef<HTMLElement | null>(null);
  const [resizing, setResizing] = useState(false);

  function handleResizeStart(event: ReactPointerEvent<HTMLSpanElement>) {
    const column = columnRef.current;
    if (!column) {
      return;
    }

    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    setResizing(true);

    const startX = event.clientX;
    const startWidth = column.offsetWidth;

    function handleMove(moveEvent: globalThis.PointerEvent) {
      const nextWidth = Math.min(
        COLUMN_MAX_WIDTH,
        Math.max(COLUMN_MIN_WIDTH, startWidth + moveEvent.clientX - startX),
      );
      onResize(status, nextWidth);
    }

    function handleUp() {
      setResizing(false);
      window.removeEventListener("pointermove", handleMove);
      window.removeEventListener("pointerup", handleUp);
      window.removeEventListener("pointercancel", handleUp);
    }

    window.addEventListener("pointermove", handleMove);
    window.addEventListener("pointerup", handleUp);
    window.addEventListener("pointercancel", handleUp);
  }

  return (
    <article
      className={`kanban-column ${isOver ? "is-over" : ""} ${width !== undefined ? "is-resized" : ""}`}
      ref={(node) => {
        setNodeRef(node);
        columnRef.current = node;
      }}
      style={width !== undefined ? { "--kanban-column-width": `${width}px` } as CSSProperties : undefined}
    >
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

      <span
        aria-hidden="true"
        className={`kanban-resize-handle ${resizing ? "is-resizing" : ""}`}
        onDoubleClick={() => onResetWidth(status)}
        onPointerDown={handleResizeStart}
        title="Drag to resize, double-click to reset"
      />
    </article>
  );
}

// closestCorners сам по себе на канбане резолвит дроп в соседнюю карточку
// исходной колонки (у высоких колонок углы всегда «дальше», чем у карточек),
// поэтому сперва берём то, что реально под курсором, а closestCorners
// оставляем только как фолбэк для клавиатурного перетаскивания.
const detectKanbanCollision: CollisionDetection = (args) => {
  const pointerCollisions = pointerWithin(args);
  return pointerCollisions.length > 0 ? pointerCollisions : closestCorners(args);
};

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
  const [columnWidths, setColumnWidths] = useState<ColumnWidths>(loadStoredColumnWidths);

  const handleColumnResize = useCallback((status: TaskStatus, width: number) => {
    setColumnWidths((current) => ({ ...current, [status]: width }));
  }, []);

  const handleColumnResetWidth = useCallback((status: TaskStatus) => {
    setColumnWidths((current) => {
      const next = { ...current };
      delete next[status];
      return next;
    });
  }, []);

  useEffect(() => {
    try {
      localStorage.setItem(COLUMN_WIDTHS_STORAGE_KEY, JSON.stringify(columnWidths));
    } catch {
      // Приватный режим или заполненное хранилище — ширина просто не сохранится.
    }
  }, [columnWidths]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 150, tolerance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const loadBoard = useCallback(async () => {
    try {
      const [tasksData, usersData, labelsData] = await Promise.all([
        getTasks(),
        getUsers(),
        getLabels(),
      ]);

      setTasks(tasksData);
      setUsers(usersData);
      setLabels(labelsData);
      setError("");
    } catch {
      setError("Could not load the board.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadBoard();
  }, [loadBoard]);

  useEffect(() => {
    function handleGithubSyncCompleted() {
      loadBoard();
    }

    window.addEventListener(GITHUB_SYNC_COMPLETED_EVENT, handleGithubSyncCompleted);
    return () => window.removeEventListener(GITHUB_SYNC_COMPLETED_EVENT, handleGithubSyncCompleted);
  }, [loadBoard]);

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
      setBoardError("Could not update the task status.");
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

      {loading && <section className="page-panel">Loading tasks...</section>}
      {error && <section className="page-panel state-error">{error}</section>}
      {!loading && !error && tasks.length === 0 && (
        <section className="page-panel">No tasks yet.</section>
      )}
      {!loading && !error && tasks.length > 0 && filteredTasks.length === 0 && (
        <section className="page-panel">No tasks found.</section>
      )}
      {!loading && !error && tasks.length > 0 && (
        <DndContext collisionDetection={detectKanbanCollision} onDragEnd={handleDragEnd} sensors={sensors}>
          <section className="kanban-board" aria-label="Project kanban board">
            {kanbanStatuses.map((status) => (
              <KanbanColumn
                key={status}
                onResetWidth={handleColumnResetWidth}
                onResize={handleColumnResize}
                status={status}
                tasks={filteredTasks.filter((task) => task.status === status)}
                width={columnWidths[status]}
              />
            ))}
          </section>
        </DndContext>
      )}

      {boardError && <section className="page-panel state-error">{boardError}</section>}
    </main>
  );
}
