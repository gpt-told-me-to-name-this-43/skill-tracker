import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import {
  approveTask,
  createTaskAttachment,
  deleteTaskAttachment,
  getLabels,
  getTaskById,
  getTasks,
  setRelatedTasks,
  setTaskLabels,
  updateTaskStatus,
} from "../../api/tasksApi";
import StatusBadge from "../../components/StatusBadge/StatusBadge";
import { statusLabels } from "../../constants/taskStatus";
import type { Label, TaskDetail, TaskListItem, TaskStatus } from "../../types/task";

const statuses: TaskStatus[] = ["todo", "in_progress", "review", "done"];

function canChangeStatus(task: TaskDetail, nextStatus: TaskStatus) {
  if (task.status === nextStatus) {
    return false;
  }

  return nextStatus !== "done" || (task.status === "review" && Boolean(task.approved_at));
}

function formatUser(user: TaskDetail["creator"] | TaskDetail["assignee"]) {
  if (!user) {
    return "Unassigned";
  }

  return user.position ? `${user.username}, ${user.position}` : user.username;
}

export default function TaskDetailsPage() {
  const { taskId } = useParams();
  const [task, setTask] = useState<TaskDetail | null>(null);
  const [allTasks, setAllTasks] = useState<TaskListItem[]>([]);
  const [labels, setLabels] = useState<Label[]>([]);
  const [selectedLabels, setSelectedLabels] = useState<number[]>([]);
  const [selectedRelated, setSelectedRelated] = useState<number[]>([]);
  const [attachmentName, setAttachmentName] = useState("");
  const [attachmentUrl, setAttachmentUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [statusError, setStatusError] = useState("");

  const taskNumericId = Number(taskId);
  const relatedOptions = useMemo(
    () => allTasks.filter((item) => item.id !== taskNumericId),
    [allTasks, taskNumericId],
  );

  useEffect(() => {
    async function loadTask() {
      if (!taskNumericId) {
        setError("Некорректный id задачи.");
        setLoading(false);
        return;
      }

      try {
        const [taskData, labelsData, tasksData] = await Promise.all([
          getTaskById(taskNumericId),
          getLabels(),
          getTasks(),
        ]);

        if (!taskData) {
          setError("Задача не найдена.");
          return;
        }

        setTask(taskData);
        setLabels(labelsData);
        setAllTasks(tasksData);
        setSelectedLabels(taskData.labels.map((label) => label.id));
        setSelectedRelated(taskData.related_tasks.map((item) => item.id));
      } catch {
        setError("Не удалось загрузить задачу.");
      } finally {
        setLoading(false);
      }
    }

    loadTask();
  }, [taskNumericId]);

  async function handleStatusChange(status: TaskStatus) {
    if (!task) {
      return;
    }

    setStatusError("");

    try {
      const updatedTask = await updateTaskStatus(task.id, status);
      if (!updatedTask) {
        throw new Error("Empty task response");
      }

      setTask(updatedTask);
    } catch {
      setStatusError("Не удалось обновить статус задачи.");
    }
  }

  async function handleApprove() {
    if (!task) {
      return;
    }

    setStatusError("");

    try {
      const approvedTask = await approveTask(task.id);
      if (!approvedTask) {
        throw new Error("Empty task response");
      }

      setTask(approvedTask);
    } catch {
      setStatusError("Не удалось апрувнуть задачу.");
    }
  }

  async function handleSaveLabels() {
    if (!task) {
      return;
    }

    setSaving(true);
    setStatusError("");
    try {
      const updatedTask = await setTaskLabels(task.id, selectedLabels);
      setTask(updatedTask);
    } catch {
      setStatusError("Не удалось сохранить метки.");
    } finally {
      setSaving(false);
    }
  }

  async function handleAddAttachment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!task) {
      return;
    }

    setSaving(true);
    setStatusError("");
    try {
      const attachment = await createTaskAttachment(task.id, {
        name: attachmentName,
        url: attachmentUrl,
      });
      setTask({ ...task, attachments: [...task.attachments, attachment], attachments_count: task.attachments_count + 1 });
      setAttachmentName("");
      setAttachmentUrl("");
    } catch {
      setStatusError("Не удалось добавить вложение.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDeleteAttachment(attachmentId: number) {
    if (!task) {
      return;
    }

    setSaving(true);
    setStatusError("");
    try {
      await deleteTaskAttachment(task.id, attachmentId);
      setTask({
        ...task,
        attachments: task.attachments.filter((attachment) => attachment.id !== attachmentId),
        attachments_count: Math.max(0, task.attachments_count - 1),
      });
    } catch {
      setStatusError("Не удалось удалить вложение.");
    } finally {
      setSaving(false);
    }
  }

  async function handleSaveRelated() {
    if (!task) {
      return;
    }

    setSaving(true);
    setStatusError("");
    try {
      const relatedTasks = await setRelatedTasks(task.id, selectedRelated);
      setTask({ ...task, related_tasks: relatedTasks, related_tasks_count: relatedTasks.length });
    } catch {
      setStatusError("Не удалось сохранить связанные задачи.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <main className="page-shell">
        <section className="page-panel">Загрузка задачи...</section>
      </main>
    );
  }

  if (error || !task) {
    return (
      <main className="page-shell">
        <section className="page-panel state-error">{error}</section>
        <Link className="page-link" to="/tasks">Назад к задачам</Link>
      </main>
    );
  }

  return (
    <main className="page-shell">
      <header className="page-header page-header-row">
        <section>
          <p>Task #{task.id}</p>
          <h1>{task.title}</h1>
        </section>
        <StatusBadge status={task.status} />
      </header>

      <section className="task-detail-layout">
        <section className="page-panel task-detail-main">
          <p>{task.description || "No description"}</p>

          <section className="detail-grid">
            <article>
              <span>Created by</span>
              <strong>{formatUser(task.creator)}</strong>
            </article>
            <article>
              <span>Assignee</span>
              <strong>{formatUser(task.assignee)}</strong>
            </article>
            <article>
              <span>Difficulty</span>
              <strong>{task.difficulty}/5</strong>
            </article>
            <article>
              <span>Deadline</span>
              <strong>{task.deadline ?? "No deadline"}</strong>
            </article>
            <article>
              <span>Created</span>
              <strong>{task.created_at}</strong>
            </article>
            <article>
              <span>Updated</span>
              <strong>{task.updated_at}</strong>
            </article>
            <article>
              <span>Approval</span>
              <strong>{task.approved_at ? `Approved by #${task.approved_by_id}` : "Not approved"}</strong>
            </article>
          </section>

          <section className="actions-row">
            <button disabled={saving || task.status !== "review" || Boolean(task.approved_at)} onClick={handleApprove} type="button">
              Approve review
            </button>
            {statuses.map((status) => (
              <button disabled={saving || !canChangeStatus(task, status)} key={status} onClick={() => handleStatusChange(status)} type="button">
                {statusLabels[status]}
              </button>
            ))}
          </section>
        </section>

        <aside className="task-detail-side">
          <section className="page-panel detail-editor">
            <header className="section-header">
              <p>Labels</p>
              <h2>Task labels</h2>
            </header>
            {labels.length === 0 && <p>No labels</p>}
            <section className="checkbox-list">
              {labels.map((label) => (
                <label key={label.id}>
                  <input
                    checked={selectedLabels.includes(label.id)}
                    onChange={(event) => {
                      setSelectedLabels((current) => (
                        event.target.checked
                          ? [...current, label.id]
                          : current.filter((id) => id !== label.id)
                      ));
                    }}
                    type="checkbox"
                  />
                  <span>{label.name}</span>
                </label>
              ))}
            </section>
            <button disabled={saving} onClick={handleSaveLabels} type="button">Save labels</button>
          </section>

          <section className="page-panel detail-editor">
            <header className="section-header">
              <p>Attachments</p>
              <h2>Files and links</h2>
            </header>
            {task.attachments.length === 0 && <p>No attachments</p>}
            <ul className="detail-list">
              {task.attachments.map((attachment) => (
                <li key={attachment.id}>
                  <a className="page-link" href={attachment.url} rel="noreferrer" target="_blank">
                    {attachment.name}
                  </a>
                  <button disabled={saving} onClick={() => handleDeleteAttachment(attachment.id)} type="button">
                    Delete
                  </button>
                </li>
              ))}
            </ul>
            <form className="compact-form" onSubmit={handleAddAttachment}>
              <input onChange={(event) => setAttachmentName(event.target.value)} placeholder="Attachment name" required value={attachmentName} />
              <input onChange={(event) => setAttachmentUrl(event.target.value)} placeholder="https://example.com/file" required type="url" value={attachmentUrl} />
              <button disabled={saving} type="submit">Add attachment</button>
            </form>
          </section>

          <section className="page-panel detail-editor">
            <header className="section-header">
              <p>Related</p>
              <h2>Related issues</h2>
            </header>
            {relatedOptions.length === 0 && <p>No tasks to relate</p>}
            <section className="checkbox-list">
              {relatedOptions.map((item) => (
                <label key={item.id}>
                  <input
                    checked={selectedRelated.includes(item.id)}
                    onChange={(event) => {
                      setSelectedRelated((current) => (
                        event.target.checked
                          ? [...current, item.id]
                          : current.filter((id) => id !== item.id)
                      ));
                    }}
                    type="checkbox"
                  />
                  <span>#{item.id} {item.title}</span>
                </label>
              ))}
            </section>
            <button disabled={saving} onClick={handleSaveRelated} type="button">Save related</button>
          </section>
        </aside>
      </section>

      {statusError && <section className="page-panel state-error">{statusError}</section>}
      <Link className="page-link" to="/tasks">Назад к задачам</Link>
    </main>
  );
}
