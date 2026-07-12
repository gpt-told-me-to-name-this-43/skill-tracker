import { useEffect, useState } from "react";
import type { DragEvent, FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import {
  createTask,
  getLabels,
  getSkills,
  getTasks,
  setRelatedTasks,
  setTaskLabels,
  setTaskSkills,
  uploadTaskAttachment,
} from "../../api/tasksApi";
import { getUsers, type User } from "../../api/usersApi";
import MarkdownRenderer from "../../components/MarkdownRenderer/MarkdownRenderer";
import type { Label, Skill, TaskListItem } from "../../types/task";
import { toApiDateTime } from "../../utils/dateTime";

export default function CreateTaskPage() {
  const navigate = useNavigate();
  const [users, setUsers] = useState<User[]>([]);
  const [labels, setLabels] = useState<Label[]>([]);
  const [skills, setSkills] = useState<Skill[]>([]);
  const [tasks, setTasks] = useState<TaskListItem[]>([]);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [deadline, setDeadline] = useState("");
  const [difficulty, setDifficulty] = useState("3");
  const [assignee, setAssignee] = useState("");
  const [selectedLabels, setSelectedLabels] = useState<number[]>([]);
  const [selectedRelated, setSelectedRelated] = useState<number[]>([]);
  const [selectedSkillRewards, setSelectedSkillRewards] = useState<Record<number, number>>({});
  const [files, setFiles] = useState<File[]>([]);
  const [dragActive, setDragActive] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadFormData() {
      try {
        const [usersData, labelsData, skillsData, tasksData] = await Promise.all([
          getUsers(),
          getLabels(),
          getSkills(),
          getTasks(),
        ]);
        setUsers(usersData);
        setLabels(labelsData);
        setSkills(skillsData);
        setTasks(tasksData);
      } catch {
        setError("Could not load task creation data.");
      } finally {
        setLoading(false);
      }
    }

    loadFormData();
  }, []);

  function toggleNumber(values: number[], value: number, checked: boolean) {
    return checked ? [...values, value] : values.filter((item) => item !== value);
  }

  function addFiles(nextFiles: FileList | File[]) {
    const incoming = Array.from(nextFiles);
    setFiles((current) => {
      const existingKeys = new Set(current.map((file) => `${file.name}:${file.size}`));
      return [
        ...current,
        ...incoming.filter((file) => !existingKeys.has(`${file.name}:${file.size}`)),
      ];
    });
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

  function handleAttachmentDrop(event: DragEvent<HTMLElement>) {
    event.preventDefault();
    event.stopPropagation();
    setDragActive(false);
    addFiles(event.dataTransfer.files);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError("");

    try {
      const task = await createTask({
        title,
        description,
        deadline: toApiDateTime(deadline),
        difficulty: Number(difficulty),
        assignee_id: assignee ? Number(assignee) : null,
      });

      await Promise.all([
        selectedLabels.length > 0 ? setTaskLabels(task.id, selectedLabels) : Promise.resolve(),
        selectedRelated.length > 0 ? setRelatedTasks(task.id, selectedRelated) : Promise.resolve(),
        Object.keys(selectedSkillRewards).length > 0
          ? setTaskSkills(
            task.id,
            Object.entries(selectedSkillRewards)
              .filter(([, reward]) => reward > 0)
              .map(([skillId, reward]) => ({
                skill_id: Number(skillId),
                exp_reward: reward,
              })),
          )
          : Promise.resolve(),
        ...files.map((file) => uploadTaskAttachment(task.id, file)),
      ]);

      navigate(`/tasks/${task.id}`);
    } catch {
      setError("Could not create the task.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="page-shell">
      <header className="page-header">
        <p>Create Task</p>
        <h1>New task</h1>
      </header>

      {loading && <section className="page-panel">Loading form data...</section>}
      {error && <section className="page-panel state-error">{error}</section>}
      {!loading && !error && (
      <form className="page-panel task-form" onSubmit={handleSubmit}>
        <label htmlFor="title">Title</label>
        <input id="title" name="title" onChange={(event) => setTitle(event.target.value)} placeholder="Create Login Page" required value={title} />

        <label htmlFor="description">Description</label>
        <textarea
          id="description"
          name="description"
          onChange={(event) => setDescription(event.target.value)}
          placeholder="You can use Markdown here"
          required
          value={description}
        />
        <section className="markdown-preview">
          <header className="section-header">
            <p>Markdown</p>
            <h2>Preview</h2>
          </header>
          <MarkdownRenderer value={description} />
        </section>

        <label htmlFor="deadline">Deadline</label>
        <input id="deadline" name="deadline" onChange={(event) => setDeadline(event.target.value)} required type="datetime-local" value={deadline} />

        <label htmlFor="difficulty">Difficulty</label>
        <select id="difficulty" name="difficulty" onChange={(event) => setDifficulty(event.target.value)} value={difficulty}>
          <option value="1">1 - Easy</option>
          <option value="2">2 - Normal</option>
          <option value="3">3 - Medium</option>
          <option value="4">4 - Hard</option>
          <option value="5">5 - Expert</option>
        </select>

        <label htmlFor="assignee">Assignee</label>
        <select id="assignee" name="assignee" onChange={(event) => setAssignee(event.target.value)} value={assignee}>
          <option value="">Unassigned</option>
          {users.map((user) => (
            <option key={user.id} value={user.id}>{user.username}</option>
          ))}
        </select>

        <section className="form-section">
          <header className="section-header">
            <p>Classification</p>
            <h2>Issue labels</h2>
          </header>
          <section className="checkbox-list">
            {labels.map((label) => (
              <label key={label.id}>
                <input
                  checked={selectedLabels.includes(label.id)}
                  onChange={(event) => setSelectedLabels((current) => toggleNumber(current, label.id, event.target.checked))}
                  type="checkbox"
                />
                <span>{label.name}</span>
              </label>
            ))}
          </section>
        </section>

        <section className="form-section">
          <header className="section-header">
            <p>Competencies</p>
            <h2>Skills XP</h2>
          </header>
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
                    onChange={(event) => setSelectedSkillRewards((current) => ({
                      ...current,
                      [skill.id]: Number(event.target.value),
                    }))}
                    type="number"
                    value={selectedSkillRewards[skill.id] ?? 50}
                  />
                </label>
              );
            })}
          </section>
        </section>

        <section className="form-section">
          <header className="section-header">
            <p>Links</p>
            <h2>Related tasks</h2>
          </header>
          {tasks.length === 0 && <p>No tasks to relate</p>}
          <section className="checkbox-list">
            {tasks.map((task) => (
              <label key={task.id}>
                <input
                  checked={selectedRelated.includes(task.id)}
                  onChange={(event) => setSelectedRelated((current) => toggleNumber(current, task.id, event.target.checked))}
                  type="checkbox"
                />
                <span>#{task.id} {task.title}</span>
              </label>
            ))}
          </section>
        </section>

        <section className="form-section">
          <header className="section-header">
            <p>Attachments</p>
            <h2>Files</h2>
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
              multiple
              onChange={(event) => {
                if (event.target.files) {
                  addFiles(event.target.files);
                }
                event.target.value = "";
              }}
              type="file"
            />
          </section>
          {files.length > 0 && (
            <ul className="detail-list">
              {files.map((file) => (
                <li key={`${file.name}-${file.size}`}>
                  <span>{file.name}</span>
                  <button onClick={() => setFiles((current) => current.filter((item) => item !== file))} type="button">
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <button className="submit-button" disabled={submitting} type="submit">
          {submitting ? "Creating..." : "Create task"}
        </button>
      </form>
      )}
    </main>
  );
}
