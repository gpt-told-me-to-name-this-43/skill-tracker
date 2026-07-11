import { useEffect, useMemo, useState } from "react";
import type { DragEvent, FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import {
  approveTask,
  createTaskAttachment,
  deleteTaskAttachment,
  getLabels,
  getSkills,
  getTaskById,
  getTaskSkills,
  getTasks,
  setRelatedTasks,
  setTaskLabels,
  setTaskSkills,
  updateTask,
  updateTaskStatus,
  uploadTaskAttachment,
} from "../../api/tasksApi";
import StatusBadge from "../../components/StatusBadge/StatusBadge";
import { statusLabels } from "../../constants/taskStatus";
import type { Label, Skill, TaskDetail, TaskListItem, TaskSkill, TaskStatus } from "../../types/task";
import { formatDateTime, toApiDateTime, toDateTimeLocalInput } from "../../utils/dateTime";

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
  const [skills, setSkills] = useState<Skill[]>([]);
  const [taskSkills, setTaskSkillsState] = useState<TaskSkill[]>([]);
  const [selectedLabels, setSelectedLabels] = useState<number[]>([]);
  const [selectedRelated, setSelectedRelated] = useState<number[]>([]);
  const [selectedSkillRewards, setSelectedSkillRewards] = useState<Record<number, number>>({});
  const [deadlineInput, setDeadlineInput] = useState("");
  const [attachmentName, setAttachmentName] = useState("");
  const [attachmentUrl, setAttachmentUrl] = useState("");
  const [dragActive, setDragActive] = useState(false);
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
        setError("Invalid task id.");
        setLoading(false);
        return;
      }

      try {
        const [taskData, labelsData, tasksData, skillsData, taskSkillsData] = await Promise.all([
          getTaskById(taskNumericId),
          getLabels(),
          getTasks(),
          getSkills(),
          getTaskSkills(taskNumericId),
        ]);

        if (!taskData) {
          setError("Task not found.");
          return;
        }

        setTask(taskData);
        setLabels(labelsData);
        setAllTasks(tasksData);
        setSkills(skillsData);
        setTaskSkillsState(taskSkillsData);
        setSelectedLabels(taskData.labels.map((label) => label.id));
        setSelectedRelated(taskData.related_tasks.map((item) => item.id));
        setSelectedSkillRewards(Object.fromEntries(
          taskSkillsData.map((item) => [item.skill.id, item.exp_reward]),
        ));
        setDeadlineInput(toDateTimeLocalInput(taskData.deadline));
      } catch {
        setError("Could not load the task.");
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
      setStatusError("Could not update the task status.");
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
      setStatusError("Could not approve the task.");
    }
  }

  async function handleSaveDeadline(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!task) {
      return;
    }

    setSaving(true);
    setStatusError("");
    try {
      const updatedTask = await updateTask(task.id, {
        deadline: deadlineInput ? toApiDateTime(deadlineInput) : null,
      });
      setTask(updatedTask);
      setDeadlineInput(toDateTimeLocalInput(updatedTask.deadline));
    } catch {
      setStatusError("Could not save the deadline.");
    } finally {
      setSaving(false);
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
      setStatusError("Could not save labels.");
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
      setStatusError("Could not add the attachment.");
    } finally {
      setSaving(false);
    }
  }

  async function handleUploadFiles(files: FileList | File[]) {
    if (!task || files.length === 0) {
      return;
    }

    setSaving(true);
    setStatusError("");
    try {
      const uploadedAttachments = await Promise.all(
        Array.from(files).map((file) => uploadTaskAttachment(task.id, file)),
      );
      setTask({
        ...task,
        attachments: [...task.attachments, ...uploadedAttachments],
        attachments_count: task.attachments_count + uploadedAttachments.length,
      });
    } catch {
      setStatusError("Could not upload the file.");
    } finally {
      setSaving(false);
      setDragActive(false);
    }
  }

  function handleAttachmentDrag(event: DragEvent<HTMLElement>) {
    event.preventDefault();
    event.stopPropagation();
    if (event.type === "dragenter" || event.type === "dragover") {
      setDragActive(true);
    }
    if (event.type === "dragleave") {
      setDragActive(false);
    }
  }

  async function handleAttachmentDrop(event: DragEvent<HTMLElement>) {
    event.preventDefault();
    event.stopPropagation();
    setDragActive(false);
    await handleUploadFiles(event.dataTransfer.files);
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
      setStatusError("Could not delete the attachment.");
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
      setStatusError("Could not save related issues.");
    } finally {
      setSaving(false);
    }
  }

  async function handleSaveSkills() {
    if (!task) {
      return;
    }

    setSaving(true);
    setStatusError("");
    try {
      const payload = Object.entries(selectedSkillRewards)
        .filter(([, reward]) => reward > 0)
        .map(([skillId, reward]) => ({
          skill_id: Number(skillId),
          exp_reward: reward,
        }));
      const updatedSkills = await setTaskSkills(task.id, payload);
      setTaskSkillsState(updatedSkills);
      setSelectedSkillRewards(Object.fromEntries(
        updatedSkills.map((item) => [item.skill.id, item.exp_reward]),
      ));
    } catch {
      setStatusError("Could not save competencies.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <main className="page-shell">
        <section className="page-panel">Loading task...</section>
      </main>
    );
  }

  if (error || !task) {
    return (
      <main className="page-shell">
        <section className="page-panel state-error">{error}</section>
        <Link className="page-link" to="/tasks">Back to tasks</Link>
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
              <form className="deadline-editor" onSubmit={handleSaveDeadline}>
                <input
                  aria-label="Deadline"
                  id="task-deadline"
                  onChange={(event) => setDeadlineInput(event.target.value)}
                  type="datetime-local"
                  value={deadlineInput}
                />
                <button disabled={saving} type="submit">
                  Save
                </button>
              </form>
            </article>
            <article>
              <span>Created</span>
              <strong>{formatDateTime(task.created_at)}</strong>
            </article>
            <article>
              <span>Updated</span>
              <strong>{formatDateTime(task.updated_at)}</strong>
            </article>
            <article>
              <span>Approval</span>
              <strong>{task.approved_at ? `Approved by #${task.approved_by_id}` : "Not approved"}</strong>
            </article>
          </section>

          <section className="status-checklist">
            <button disabled={saving || task.status !== "review" || Boolean(task.approved_at)} onClick={handleApprove} type="button">
              Approve review
            </button>
            {statuses.map((status) => (
              <button
                className={task.status === status ? "is-checked" : ""}
                disabled={saving || !canChangeStatus(task, status)}
                key={status}
                onClick={() => handleStatusChange(status)}
                type="button"
              >
                <span>{task.status === status ? "✓" : ""}</span>
                {statusLabels[status]}
              </button>
            ))}
          </section>
        </section>

        <aside className="task-detail-side">
          <section className="page-panel detail-editor">
            <header className="section-header">
              <p>Classification</p>
              <h2>Issue labels</h2>
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
            <section
              className={`attachment-dropzone ${dragActive ? "is-active" : ""}`}
              onDragEnter={handleAttachmentDrag}
              onDragLeave={handleAttachmentDrag}
              onDragOver={handleAttachmentDrag}
              onDrop={handleAttachmentDrop}
            >
              <strong>Drop files here</strong>
              <span>or choose files from your computer</span>
              <input
                disabled={saving}
                multiple
                onChange={(event) => {
                  if (event.target.files) {
                    handleUploadFiles(event.target.files);
                  }
                  event.target.value = "";
                }}
                type="file"
              />
            </section>
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
              <p>Competencies</p>
              <h2>Skills XP</h2>
            </header>
            {skills.length === 0 && <p>No skills yet</p>}
            {taskSkills.length > 0 && (
              <ul className="skill-reward-summary">
                {taskSkills.map((item) => (
                  <li key={item.skill.id}>
                    <span>{item.skill.name}</span>
                    <strong>{item.exp_reward} XP</strong>
                  </li>
                ))}
              </ul>
            )}
            <section className="skill-reward-list">
              {skills.map((skill) => {
                const selected = selectedSkillRewards[skill.id] !== undefined;

                return (
                  <label className={selected ? "is-selected" : ""} key={skill.id}>
                    <input
                      checked={selected}
                      onChange={(event) => {
                        setSelectedSkillRewards((current) => {
                          if (!event.target.checked) {
                            const next = { ...current };
                            delete next[skill.id];
                            return next;
                          }
                          return { ...current, [skill.id]: current[skill.id] ?? 50 };
                        });
                      }}
                      type="checkbox"
                    />
                    <span>{skill.name}</span>
                    <input
                      disabled={!selected}
                      min="1"
                      max="1000"
                      onChange={(event) => {
                        setSelectedSkillRewards((current) => ({
                          ...current,
                          [skill.id]: Number(event.target.value),
                        }));
                      }}
                      type="number"
                      value={selectedSkillRewards[skill.id] ?? 50}
                    />
                  </label>
                );
              })}
            </section>
            <button disabled={saving} onClick={handleSaveSkills} type="button">Save competencies</button>
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
      <Link className="page-link" to="/tasks">Back to tasks</Link>
    </main>
  );
}
