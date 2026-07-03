export default function CreateTaskPage() {
  return (
    <main className="page-shell">
      <header className="page-header">
        <p>Create Task</p>
        <h1>New task</h1>
      </header>

      <form className="page-panel task-form">
        <label htmlFor="title">Title</label>
        <input id="title" name="title" placeholder="Create Login Page" />

        <label htmlFor="description">Description</label>
        <textarea id="description" name="description" placeholder="Describe what should be done" />

        <label htmlFor="deadline">Deadline</label>
        <input id="deadline" name="deadline" type="date" />

        <label htmlFor="difficulty">Difficulty</label>
        <select id="difficulty" name="difficulty" defaultValue="3">
          <option value="1">1 - Easy</option>
          <option value="2">2 - Normal</option>
          <option value="3">3 - Medium</option>
          <option value="4">4 - Hard</option>
          <option value="5">5 - Expert</option>
        </select>

        <label htmlFor="assignee">Assignee</label>
        <input id="assignee" name="assignee" placeholder="John" />

        <button className="submit-button" type="submit">Create task</button>
      </form>
    </main>
  );
}
