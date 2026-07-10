import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

type TaskStatus = 'todo' | 'in_progress' | 'review' | 'done'
type MemberStatus = 'active' | 'away' | 'inactive'

type DemoUser = {
  id: number
  email: string
  username: string
  role: string
  avatar_url: string | null
  position: string | null
  member_status: MemberStatus
  created_at: string
}

type DemoLabel = {
  id: number
  name: string
  color: string | null
  created_at: string
  updated_at: string
}

type DemoAttachment = {
  id: number
  task_id: number
  name: string
  url: string
  created_by_id: number
  created_at: string
}

type DemoTeam = {
  id: number
  name: string
  description: string | null
  lead_id: number | null
  member_ids: number[]
  created_at: string
  updated_at: string
}

type DemoTask = {
  id: number
  title: string
  description: string | null
  status: TaskStatus
  difficulty: number
  deadline: string | null
  creator_id: number
  assignee_id: number | null
  label_ids: number[]
  approved_by_id: number | null
  approved_at: string | null
  created_at: string
  updated_at: string
}

const today = '2026-07-10'

const demoUsers: DemoUser[] = [
  { id: 1, email: 'frontend@example.com', username: 'Anna Frontend', role: 'admin', avatar_url: null, position: 'Frontend Lead', member_status: 'active', created_at: '2026-07-01' },
  { id: 2, email: 'backend@example.com', username: 'Ivan Backend', role: 'user', avatar_url: null, position: 'Backend Lead', member_status: 'active', created_at: '2026-07-01' },
  { id: 3, email: 'design@example.com', username: 'Kate Design', role: 'user', avatar_url: null, position: 'Product Designer', member_status: 'away', created_at: '2026-07-02' },
  { id: 4, email: 'qa@example.com', username: 'Max QA', role: 'user', avatar_url: null, position: 'QA Engineer', member_status: 'active', created_at: '2026-07-02' },
]

const demoLabels: DemoLabel[] = [
  { id: 1, name: 'Backend', color: '#2563eb', created_at: today, updated_at: today },
  { id: 2, name: 'Frontend', color: '#16a34a', created_at: today, updated_at: today },
  { id: 3, name: 'Design', color: '#9333ea', created_at: today, updated_at: today },
  { id: 4, name: 'QA', color: '#ea580c', created_at: today, updated_at: today },
  { id: 5, name: 'Feature', color: '#0891b2', created_at: today, updated_at: today },
  { id: 6, name: 'Bug', color: '#dc2626', created_at: today, updated_at: today },
  { id: 7, name: 'Documentation', color: '#64748b', created_at: today, updated_at: today },
  { id: 8, name: 'Enhancement', color: '#0f766e', created_at: today, updated_at: today },
]

const demoTeams: DemoTeam[] = [
  { id: 1, name: 'Frontend Team', description: 'Builds the product interface.', lead_id: 1, member_ids: [1], created_at: today, updated_at: today },
  { id: 2, name: 'Backend Team', description: 'Owns API and domain logic.', lead_id: 2, member_ids: [2], created_at: today, updated_at: today },
  { id: 3, name: 'Design Team', description: 'Shapes product experience.', lead_id: 3, member_ids: [3], created_at: today, updated_at: today },
  { id: 4, name: 'QA Team', description: 'Keeps release quality visible.', lead_id: 4, member_ids: [4], created_at: today, updated_at: today },
]

const demoTasks: DemoTask[] = [
  { id: 1, title: 'Design project members panel', description: 'Add project people, teams, leads and member statuses.', status: 'todo', difficulty: 2, deadline: '2026-07-15', creator_id: 3, assignee_id: 1, label_ids: [2, 3, 5], approved_by_id: null, approved_at: null, created_at: '2026-07-08', updated_at: today },
  { id: 2, title: 'Implement Kanban drag and drop', description: 'Move tasks between columns and persist status changes through the API layer.', status: 'in_progress', difficulty: 4, deadline: '2026-07-16', creator_id: 1, assignee_id: 1, label_ids: [2, 8], approved_by_id: null, approved_at: null, created_at: '2026-07-08', updated_at: today },
  { id: 3, title: 'Review task approval workflow', description: 'Check status transitions before marking tasks done.', status: 'review', difficulty: 3, deadline: '2026-07-17', creator_id: 2, assignee_id: 4, label_ids: [1, 4, 6], approved_by_id: null, approved_at: null, created_at: '2026-07-09', updated_at: today },
  { id: 4, title: 'Prepare task card metadata', description: 'Show labels, attachments, authors, assignees and related issues in cards.', status: 'done', difficulty: 2, deadline: '2026-07-12', creator_id: 1, assignee_id: 3, label_ids: [5, 7], approved_by_id: 4, approved_at: today, created_at: '2026-07-07', updated_at: today },
]

let demoAttachments: DemoAttachment[] = [
  { id: 1, task_id: 1, name: 'people-layout.fig', url: 'https://example.com/people-layout.fig', created_by_id: 3, created_at: today },
  { id: 2, task_id: 3, name: 'status-rules.md', url: 'https://example.com/status-rules.md', created_by_id: 2, created_at: today },
  { id: 3, task_id: 4, name: 'task-card-notes.md', url: 'https://example.com/task-card-notes.md', created_by_id: 1, created_at: today },
]

let demoRelations = [
  { left_task_id: 1, right_task_id: 4 },
  { left_task_id: 2, right_task_id: 3 },
]

function userSummary(userId: number | null) {
  const user = demoUsers.find((item) => item.id === userId)
  if (!user) {
    return null
  }

  return {
    id: user.id,
    username: user.username,
    avatar_url: user.avatar_url,
    position: user.position,
    member_status: user.member_status,
  }
}

function person(user: DemoUser) {
  const team = demoTeams.find((item) => item.member_ids.includes(user.id))
  return {
    ...userSummary(user.id),
    role: user.role,
    team: team ? { id: team.id, name: team.name } : null,
  }
}

function teamDto(team: DemoTeam) {
  return {
    id: team.id,
    name: team.name,
    description: team.description,
    member_count: team.member_ids.length,
    lead: userSummary(team.lead_id),
    members: team.member_ids.map(userSummary).filter(Boolean),
    created_at: team.created_at,
    updated_at: team.updated_at,
  }
}

function relatedTasks(taskId: number) {
  return demoRelations
    .filter((relation) => relation.left_task_id === taskId || relation.right_task_id === taskId)
    .map((relation) => relation.left_task_id === taskId ? relation.right_task_id : relation.left_task_id)
    .map((id) => demoTasks.find((task) => task.id === id))
    .filter(Boolean)
    .map((task) => ({ id: task!.id, title: task!.title, status: task!.status }))
}

function taskDto(task: DemoTask, detail = false) {
  const base = {
    id: task.id,
    title: task.title,
    status: task.status,
    difficulty: task.difficulty,
    deadline: task.deadline,
    creator: userSummary(task.creator_id),
    assignee: userSummary(task.assignee_id),
    labels: demoLabels.filter((label) => task.label_ids.includes(label.id)),
    attachments_count: demoAttachments.filter((attachment) => attachment.task_id === task.id).length,
    related_tasks_count: relatedTasks(task.id).length,
    created_at: task.created_at,
    updated_at: task.updated_at,
  }

  if (!detail) {
    return base
  }

  return {
    ...base,
    description: task.description,
    attachments: demoAttachments
      .filter((attachment) => attachment.task_id === task.id)
      .map((attachment) => ({ ...attachment, created_by: userSummary(attachment.created_by_id) })),
    related_tasks: relatedTasks(task.id),
    creator_id: task.creator_id,
    assignee_id: task.assignee_id,
    approved_by_id: task.approved_by_id,
    approved_at: task.approved_at,
  }
}

function sendJson(res: { setHeader: (key: string, value: string) => void; statusCode: number; end: (body: string) => void }, status: number, data: unknown) {
  res.statusCode = status
  res.setHeader('Content-Type', 'application/json')
  res.end(JSON.stringify(data))
}

async function readBody(req: { on: (event: string, callback: (chunk?: Buffer) => void) => void }) {
  const chunks: Buffer[] = []

  return new Promise<Record<string, unknown>>((resolve) => {
    req.on('data', (chunk) => {
      if (chunk) {
        chunks.push(chunk)
      }
    })
    req.on('end', () => {
      const body = Buffer.concat(chunks).toString()
      resolve(body ? JSON.parse(body) : {})
    })
  })
}

function parseQuery(url: string) {
  return new URL(url, 'http://localhost').searchParams
}

function replaceRelations(taskId: number, taskIds: number[]) {
  demoRelations = demoRelations.filter((relation) => relation.left_task_id !== taskId && relation.right_task_id !== taskId)
  taskIds.forEach((relatedId) => {
    const [left_task_id, right_task_id] = [taskId, relatedId].sort((a, b) => a - b)
    if (left_task_id !== right_task_id) {
      demoRelations.push({ left_task_id, right_task_id })
    }
  })
}

function demoApiPlugin() {
  return {
    name: 'demo-api',
    enforce: 'pre' as const,
    configureServer(server: { middlewares: { use: (handler: (req: any, res: any, next: () => void) => void) => void } }) {
      if (process.env.VITE_DEMO_API === 'false') {
        return
      }

      server.middlewares.use(async (req, res, next) => {
        const method = req.method ?? 'GET'
        const url = req.url ?? ''
        const path = url.split('?')[0]

        if (!path.startsWith('/api/v1')) {
          next()
          return
        }

        if (path === '/api/v1/auth/login' && method === 'POST') {
          sendJson(res, 200, { access_token: 'demo-token', token_type: 'bearer' })
          return
        }

        if (path === '/api/v1/auth/me' && method === 'GET') {
          sendJson(res, 200, demoUsers[0])
          return
        }

        if (path === '/api/v1/users' && method === 'GET') {
          const query = parseQuery(url)
          let users = demoUsers.map(person)
          const teamId = Number(query.get('team_id'))
          const memberStatus = query.get('member_status')
          if (teamId) {
            users = users.filter((user) => user.team?.id === teamId)
          }
          if (memberStatus) {
            users = users.filter((user) => user.member_status === memberStatus)
          }
          sendJson(res, 200, users)
          return
        }

        const profileMatch = path.match(/^\/api\/v1\/users\/(\d+)\/workspace-profile$/)
        if (profileMatch && method === 'PATCH') {
          const body = await readBody(req)
          const user = demoUsers.find((item) => item.id === Number(profileMatch[1]))
          if (!user) {
            sendJson(res, 404, { detail: 'User not found' })
            return
          }
          if ('avatar_url' in body) user.avatar_url = body.avatar_url ? String(body.avatar_url) : null
          if ('position' in body) user.position = body.position ? String(body.position) : null
          if ('member_status' in body) user.member_status = body.member_status as MemberStatus
          sendJson(res, 200, person(user))
          return
        }

        if (path === '/api/v1/teams' && method === 'GET') {
          sendJson(res, 200, demoTeams.map(teamDto))
          return
        }

        if (path === '/api/v1/teams' && method === 'POST') {
          const body = await readBody(req)
          const team: DemoTeam = {
            id: Math.max(0, ...demoTeams.map((item) => item.id)) + 1,
            name: String(body.name ?? 'New team'),
            description: body.description ? String(body.description) : null,
            lead_id: null,
            member_ids: [],
            created_at: today,
            updated_at: today,
          }
          demoTeams.push(team)
          sendJson(res, 201, teamDto(team))
          return
        }

        const teamMatch = path.match(/^\/api\/v1\/teams\/(\d+)$/)
        if (teamMatch && method === 'PATCH') {
          const body = await readBody(req)
          const team = demoTeams.find((item) => item.id === Number(teamMatch[1]))
          if (!team) {
            sendJson(res, 404, { detail: 'Team not found' })
            return
          }
          if ('name' in body) team.name = String(body.name)
          if ('description' in body) team.description = body.description ? String(body.description) : null
          team.updated_at = today
          sendJson(res, 200, teamDto(team))
          return
        }

        const teamMembersMatch = path.match(/^\/api\/v1\/teams\/(\d+)\/members$/)
        if (teamMembersMatch && method === 'PUT') {
          const body = await readBody(req)
          const team = demoTeams.find((item) => item.id === Number(teamMembersMatch[1]))
          if (!team) {
            sendJson(res, 404, { detail: 'Team not found' })
            return
          }
          const userIds = Array.isArray(body.user_ids) ? body.user_ids.map(Number) : []
          demoTeams.forEach((item) => {
            item.member_ids = item.member_ids.filter((id) => !userIds.includes(id) || item.id === team.id)
            if (item.id !== team.id && item.lead_id && userIds.includes(item.lead_id)) {
              item.lead_id = null
            }
          })
          team.member_ids = userIds
          team.lead_id = body.lead_id ? Number(body.lead_id) : null
          team.updated_at = today
          sendJson(res, 200, teamDto(team))
          return
        }

        if (path.match(/^\/api\/v1\/users\/\d+\/skills$/) && method === 'GET') {
          sendJson(res, 200, [
            { skill: { id: 1, name: 'React', description: null }, level: 7, experience: 720, current_level_xp: 700, next_level_xp: 800, progress_to_next_level: 20 },
            { skill: { id: 2, name: 'TypeScript', description: null }, level: 6, experience: 640, current_level_xp: 600, next_level_xp: 700, progress_to_next_level: 40 },
          ])
          return
        }

        if (path.match(/^\/api\/v1\/users\/\d+\/progress$/) && method === 'GET') {
          sendJson(res, 200, { user_id: 1, total_experience: 1360, skills_count: 2, average_level: 6.5, skills: [] })
          return
        }

        if (path === '/api/v1/labels' && method === 'GET') {
          sendJson(res, 200, demoLabels)
          return
        }

        if (path === '/api/v1/labels' && method === 'POST') {
          const body = await readBody(req)
          const label: DemoLabel = { id: Math.max(...demoLabels.map((item) => item.id)) + 1, name: String(body.name), color: body.color ? String(body.color) : null, created_at: today, updated_at: today }
          demoLabels.push(label)
          sendJson(res, 201, label)
          return
        }

        if (path === '/api/v1/tasks' && method === 'GET') {
          sendJson(res, 200, demoTasks.map((task) => taskDto(task)))
          return
        }

        if (path === '/api/v1/tasks' && method === 'POST') {
          const body = await readBody(req)
          const task: DemoTask = {
            id: Math.max(...demoTasks.map((item) => item.id)) + 1,
            title: String(body.title ?? 'New task'),
            description: body.description ? String(body.description) : '',
            status: 'todo',
            difficulty: Number(body.difficulty ?? 3),
            deadline: typeof body.deadline === 'string' ? body.deadline : null,
            creator_id: 1,
            assignee_id: typeof body.assignee_id === 'number' ? body.assignee_id : null,
            label_ids: [5],
            approved_by_id: null,
            approved_at: null,
            created_at: today,
            updated_at: today,
          }
          demoTasks.push(task)
          sendJson(res, 201, taskDto(task, true))
          return
        }

        const taskMatch = path.match(/^\/api\/v1\/tasks\/(\d+)$/)
        if (taskMatch && method === 'GET') {
          const task = demoTasks.find((item) => item.id === Number(taskMatch[1]))
          sendJson(res, task ? 200 : 404, task ? taskDto(task, true) : { detail: 'Task not found' })
          return
        }

        const statusMatch = path.match(/^\/api\/v1\/tasks\/(\d+)\/status$/)
        if (statusMatch && method === 'PATCH') {
          const body = await readBody(req)
          const task = demoTasks.find((item) => item.id === Number(statusMatch[1]))
          if (!task) {
            sendJson(res, 404, { detail: 'Task not found' })
            return
          }
          task.status = body.status as TaskStatus
          task.updated_at = today
          sendJson(res, 200, taskDto(task, true))
          return
        }

        const approveMatch = path.match(/^\/api\/v1\/tasks\/(\d+)\/approve$/)
        if (approveMatch && method === 'PATCH') {
          const task = demoTasks.find((item) => item.id === Number(approveMatch[1]))
          if (!task) {
            sendJson(res, 404, { detail: 'Task not found' })
            return
          }
          task.approved_by_id = 1
          task.approved_at = today
          task.updated_at = today
          sendJson(res, 200, taskDto(task, true))
          return
        }

        const taskLabelsMatch = path.match(/^\/api\/v1\/tasks\/(\d+)\/labels$/)
        if (taskLabelsMatch && method === 'PUT') {
          const body = await readBody(req)
          const task = demoTasks.find((item) => item.id === Number(taskLabelsMatch[1]))
          if (!task) {
            sendJson(res, 404, { detail: 'Task not found' })
            return
          }
          task.label_ids = Array.isArray(body.label_ids) ? body.label_ids.map(Number) : []
          task.updated_at = today
          sendJson(res, 200, taskDto(task, true))
          return
        }

        const attachmentMatch = path.match(/^\/api\/v1\/tasks\/(\d+)\/attachments$/)
        if (attachmentMatch && method === 'POST') {
          const body = await readBody(req)
          const attachment: DemoAttachment = {
            id: Math.max(0, ...demoAttachments.map((item) => item.id)) + 1,
            task_id: Number(attachmentMatch[1]),
            name: String(body.name),
            url: String(body.url),
            created_by_id: 1,
            created_at: today,
          }
          demoAttachments = [...demoAttachments, attachment]
          sendJson(res, 201, { ...attachment, created_by: userSummary(1) })
          return
        }

        const attachmentDeleteMatch = path.match(/^\/api\/v1\/tasks\/(\d+)\/attachments\/(\d+)$/)
        if (attachmentDeleteMatch && method === 'DELETE') {
          demoAttachments = demoAttachments.filter((item) => item.id !== Number(attachmentDeleteMatch[2]))
          sendJson(res, 204, null)
          return
        }

        const relatedMatch = path.match(/^\/api\/v1\/tasks\/(\d+)\/related$/)
        if (relatedMatch && method === 'PUT') {
          const body = await readBody(req)
          const taskIds = Array.isArray(body.task_ids) ? body.task_ids.map(Number) : []
          replaceRelations(Number(relatedMatch[1]), taskIds)
          sendJson(res, 200, relatedTasks(Number(relatedMatch[1])))
          return
        }

        sendJson(res, 404, { detail: 'Demo endpoint not found' })
      })
    },
  }
}

export default defineConfig({
  plugins: [demoApiPlugin(), react()],
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8000',
    },
  },
})
