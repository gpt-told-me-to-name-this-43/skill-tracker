import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

type DemoTask = {
  id: number
  title: string
  description: string
  status: 'todo' | 'in_progress' | 'review' | 'done'
  difficulty: number
  deadline: string | null
  creator_id: number
  created_by: string
  assignee_id: number | null
  assignee: string | null
  labels: string[]
  attachments: { id: number; name: string; url?: string }[]
  related_issues: { id: number; title: string }[]
  approved_by_id: number | null
  approved_at: string | null
  created_at: string
  updated_at: string
}

const demoUsers = [
  {
    id: 1,
    email: 'frontend@example.com',
    username: 'Anna Frontend',
    role: 'Frontend Lead',
    avatar_url: null,
    team: 'Frontend Team',
    status: 'Active',
    created_at: '2026-07-01',
  },
  {
    id: 2,
    email: 'backend@example.com',
    username: 'Ivan Backend',
    role: 'Backend Lead',
    avatar_url: null,
    team: 'Backend Team',
    status: 'Active',
    created_at: '2026-07-01',
  },
  {
    id: 3,
    email: 'design@example.com',
    username: 'Kate Design',
    role: 'Designer',
    avatar_url: null,
    team: 'Design Team',
    status: 'Reviewing',
    created_at: '2026-07-02',
  },
  {
    id: 4,
    email: 'qa@example.com',
    username: 'Max QA',
    role: 'QA Engineer',
    avatar_url: null,
    team: 'QA Team',
    status: 'Active',
    created_at: '2026-07-02',
  },
]

const demoTasks: DemoTask[] = [
  {
    id: 1,
    title: 'Design project members panel',
    description: 'Add project people, teams, leads and member statuses to the workspace overview.',
    status: 'todo',
    difficulty: 2,
    deadline: '2026-07-15',
    creator_id: 3,
    created_by: 'Kate Design',
    assignee_id: 1,
    assignee: 'Anna Frontend',
    labels: ['Frontend', 'Design', 'Feature'],
    attachments: [{ id: 1, name: 'people-layout.fig' }],
    related_issues: [{ id: 4, title: 'Update project navigation' }],
    approved_by_id: null,
    approved_at: null,
    created_at: '2026-07-08',
    updated_at: '2026-07-10',
  },
  {
    id: 2,
    title: 'Implement Kanban drag and drop',
    description: 'Move tasks between columns and persist status changes through the API layer.',
    status: 'in_progress',
    difficulty: 4,
    deadline: '2026-07-16',
    creator_id: 1,
    created_by: 'Anna Frontend',
    assignee_id: 1,
    assignee: 'Anna Frontend',
    labels: ['Frontend', 'Enhancement'],
    attachments: [],
    related_issues: [{ id: 3, title: 'Task status API' }],
    approved_by_id: null,
    approved_at: null,
    created_at: '2026-07-08',
    updated_at: '2026-07-10',
  },
  {
    id: 3,
    title: 'Review task approval workflow',
    description: 'Check status transitions and review approval behavior before marking tasks done.',
    status: 'review',
    difficulty: 3,
    deadline: '2026-07-17',
    creator_id: 2,
    created_by: 'Ivan Backend',
    assignee_id: 4,
    assignee: 'Max QA',
    labels: ['QA', 'Backend', 'Bug'],
    attachments: [{ id: 2, name: 'status-rules.md' }],
    related_issues: [],
    approved_by_id: null,
    approved_at: null,
    created_at: '2026-07-09',
    updated_at: '2026-07-10',
  },
  {
    id: 4,
    title: 'Prepare task card metadata',
    description: 'Show labels, attachments, authors, assignees and related issues in task cards.',
    status: 'done',
    difficulty: 2,
    deadline: '2026-07-12',
    creator_id: 1,
    created_by: 'Anna Frontend',
    assignee_id: 3,
    assignee: 'Kate Design',
    labels: ['Documentation', 'Feature'],
    attachments: [{ id: 3, name: 'task-card-notes.md' }],
    related_issues: [{ id: 1, title: 'Project members panel' }],
    approved_by_id: 4,
    approved_at: '2026-07-10',
    created_at: '2026-07-07',
    updated_at: '2026-07-10',
  },
]

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
          sendJson(res, 200, demoUsers)
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
          sendJson(res, 200, {
            user_id: 1,
            total_experience: 1360,
            skills_count: 2,
            average_level: 6.5,
            skills: [
              { skill: { id: 1, name: 'React', description: null }, level: 7, experience: 720, current_level_xp: 700, next_level_xp: 800, progress_to_next_level: 20 },
              { skill: { id: 2, name: 'TypeScript', description: null }, level: 6, experience: 640, current_level_xp: 600, next_level_xp: 700, progress_to_next_level: 40 },
            ],
          })
          return
        }

        if (path === '/api/v1/tasks' && method === 'GET') {
          sendJson(res, 200, demoTasks)
          return
        }

        if (path === '/api/v1/tasks' && method === 'POST') {
          const body = await readBody(req)
          const assigneeId = typeof body.assignee_id === 'number' ? body.assignee_id : null
          const assignee = demoUsers.find((user) => user.id === assigneeId)?.username ?? null
          const task: DemoTask = {
            id: Math.max(...demoTasks.map((item) => item.id)) + 1,
            title: String(body.title ?? 'New task'),
            description: String(body.description ?? ''),
            status: 'todo',
            difficulty: Number(body.difficulty ?? 3),
            deadline: typeof body.deadline === 'string' ? body.deadline : null,
            creator_id: 1,
            created_by: demoUsers[0].username,
            assignee_id: assigneeId,
            assignee,
            labels: ['Feature'],
            attachments: [],
            related_issues: [],
            approved_by_id: null,
            approved_at: null,
            created_at: '2026-07-10',
            updated_at: '2026-07-10',
          }

          demoTasks.push(task)
          sendJson(res, 201, task)
          return
        }

        const taskMatch = path.match(/^\/api\/v1\/tasks\/(\d+)$/)
        if (taskMatch && method === 'GET') {
          const task = demoTasks.find((item) => item.id === Number(taskMatch[1]))
          sendJson(res, task ? 200 : 404, task ?? { detail: 'Task not found' })
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

          task.status = body.status as DemoTask['status']
          task.updated_at = '2026-07-10'
          sendJson(res, 200, task)
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
          task.approved_at = '2026-07-10'
          task.updated_at = '2026-07-10'
          sendJson(res, 200, task)
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
