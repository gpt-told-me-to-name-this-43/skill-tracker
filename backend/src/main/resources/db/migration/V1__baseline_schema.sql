-- Baseline schema: the exact end state of the Alembic chain
-- (f60470687a86 .. c9d1e4f7a2b5) this project migrated away from.
--
-- Databases created by Alembic are non-empty, so Flyway baselines them at V1
-- (spring.flyway.baseline-on-migrate) and skips this script. A brand new
-- database runs it and ends up with the identical schema.

CREATE TYPE taskstatus AS ENUM ('todo', 'in_progress', 'review', 'done');

CREATE TABLE skills (
    id          serial       NOT NULL,
    name        varchar(100) NOT NULL,
    description text,
    created_at  timestamp    DEFAULT now(),
    updated_at  timestamp    DEFAULT now(),
    CONSTRAINT skills_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX ix_skills_name ON skills (name);
CREATE UNIQUE INDEX ix_skills_name_lower ON skills (lower(name));

CREATE TABLE users (
    id              serial        NOT NULL,
    username        varchar(50)   NOT NULL,
    email           varchar(255)  NOT NULL,
    hashed_password varchar(255)  NOT NULL,
    role            varchar(20)   NOT NULL DEFAULT 'user',
    avatar_url      varchar(2048),
    position        varchar(100),
    member_status   varchar(20)   NOT NULL DEFAULT 'active',
    github_login    varchar(100),
    is_placeholder  boolean       NOT NULL DEFAULT false,
    created_at      timestamp     NOT NULL DEFAULT now(),
    updated_at      timestamp     NOT NULL DEFAULT now(),
    CONSTRAINT users_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX ix_users_email ON users (email);
CREATE UNIQUE INDEX ix_users_username ON users (username);
CREATE UNIQUE INDEX ix_users_github_login ON users (github_login);

CREATE TABLE tasks (
    id                  serial       NOT NULL,
    title               varchar(200) NOT NULL,
    description         text,
    status              taskstatus   NOT NULL,
    difficulty          smallint     NOT NULL,
    deadline            timestamp,
    creator_id          integer      NOT NULL,
    assignee_id         integer,
    approved_by_id      integer,
    approved_at         timestamp,
    github_issue_number integer,
    created_at          timestamp    NOT NULL DEFAULT now(),
    updated_at          timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT tasks_pkey PRIMARY KEY (id),
    CONSTRAINT tasks_creator_id_fkey FOREIGN KEY (creator_id) REFERENCES users (id),
    CONSTRAINT tasks_assignee_id_fkey FOREIGN KEY (assignee_id) REFERENCES users (id),
    CONSTRAINT fk_tasks_approved_by_id_users FOREIGN KEY (approved_by_id) REFERENCES users (id)
);
CREATE INDEX ix_tasks_status ON tasks (status);
CREATE INDEX ix_tasks_assignee_id ON tasks (assignee_id);
CREATE INDEX ix_tasks_difficulty ON tasks (difficulty);
CREATE INDEX ix_tasks_approved_by_id ON tasks (approved_by_id);
CREATE UNIQUE INDEX ix_tasks_github_issue_number ON tasks (github_issue_number);

CREATE TABLE user_skills (
    id         serial  NOT NULL,
    user_id    integer NOT NULL,
    skill_id   integer NOT NULL,
    experience integer NOT NULL,
    created_at timestamp DEFAULT now(),
    updated_at timestamp DEFAULT now(),
    CONSTRAINT user_skills_pkey PRIMARY KEY (id),
    CONSTRAINT uq_user_skill UNIQUE (user_id, skill_id),
    CONSTRAINT user_skills_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT user_skills_skill_id_fkey FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE
);
CREATE INDEX ix_user_skills_user_id ON user_skills (user_id);
CREATE INDEX ix_user_skills_skill_id ON user_skills (skill_id);
CREATE UNIQUE INDEX ix_user_skills_user_id_skill_id ON user_skills (user_id, skill_id);

CREATE TABLE experience_logs (
    id         serial    NOT NULL,
    user_id    integer   NOT NULL,
    skill_id   integer   NOT NULL,
    task_id    integer   NOT NULL,
    amount     integer   NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT experience_logs_pkey PRIMARY KEY (id),
    CONSTRAINT uq_experience_logs_task_user_skill UNIQUE (task_id, user_id, skill_id),
    CONSTRAINT ck_experience_logs_amount_positive CHECK (amount > 0),
    CONSTRAINT experience_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT experience_logs_skill_id_fkey FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE,
    CONSTRAINT experience_logs_task_id_fkey FOREIGN KEY (task_id) REFERENCES tasks (id)
);
CREATE INDEX ix_experience_logs_user_id ON experience_logs (user_id);
CREATE INDEX ix_experience_logs_skill_id ON experience_logs (skill_id);

CREATE TABLE task_skills (
    id         serial    NOT NULL,
    task_id    integer   NOT NULL,
    skill_id   integer   NOT NULL,
    exp_reward integer   NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT task_skills_pkey PRIMARY KEY (id),
    CONSTRAINT uq_task_skill UNIQUE (task_id, skill_id),
    CONSTRAINT ck_task_skills_exp_reward_positive CHECK (exp_reward > 0),
    CONSTRAINT task_skills_task_id_fkey FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT task_skills_skill_id_fkey FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE
);
CREATE INDEX ix_task_skills_task_id ON task_skills (task_id);
CREATE INDEX ix_task_skills_skill_id ON task_skills (skill_id);

CREATE TABLE teams (
    id          serial       NOT NULL,
    name        varchar(100) NOT NULL,
    description text,
    lead_id     integer,
    created_at  timestamp    NOT NULL DEFAULT now(),
    updated_at  timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT teams_pkey PRIMARY KEY (id),
    CONSTRAINT teams_lead_id_fkey FOREIGN KEY (lead_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE UNIQUE INDEX ix_teams_name_lower_unique ON teams (lower(name));
CREATE INDEX ix_teams_lead_id ON teams (lead_id);

CREATE TABLE team_members (
    id         serial    NOT NULL,
    team_id    integer   NOT NULL,
    user_id    integer   NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT team_members_pkey PRIMARY KEY (id),
    CONSTRAINT uq_team_member_team_user UNIQUE (team_id, user_id),
    CONSTRAINT uq_team_member_user UNIQUE (user_id),
    CONSTRAINT team_members_team_id_fkey FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT team_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_team_members_team_id ON team_members (team_id);
CREATE INDEX ix_team_members_user_id ON team_members (user_id);

CREATE TABLE labels (
    id         serial      NOT NULL,
    name       varchar(80) NOT NULL,
    color      varchar(7),
    created_at timestamp   NOT NULL DEFAULT now(),
    updated_at timestamp   NOT NULL DEFAULT now(),
    CONSTRAINT labels_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uq_labels_name_lower ON labels (lower(name));

CREATE TABLE task_labels (
    id         serial    NOT NULL,
    task_id    integer   NOT NULL,
    label_id   integer   NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT task_labels_pkey PRIMARY KEY (id),
    CONSTRAINT uq_task_label UNIQUE (task_id, label_id),
    CONSTRAINT task_labels_task_id_fkey FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT task_labels_label_id_fkey FOREIGN KEY (label_id) REFERENCES labels (id) ON DELETE CASCADE
);
CREATE INDEX ix_task_labels_task_id ON task_labels (task_id);
CREATE INDEX ix_task_labels_label_id ON task_labels (label_id);

CREATE TABLE task_attachments (
    id            serial        NOT NULL,
    task_id       integer       NOT NULL,
    name          varchar(200)  NOT NULL,
    url           varchar(1000) NOT NULL,
    created_by_id integer       NOT NULL,
    created_at    timestamp     NOT NULL DEFAULT now(),
    CONSTRAINT task_attachments_pkey PRIMARY KEY (id),
    CONSTRAINT task_attachments_task_id_fkey FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT task_attachments_created_by_id_fkey FOREIGN KEY (created_by_id) REFERENCES users (id)
);
CREATE INDEX ix_task_attachments_task_id ON task_attachments (task_id);

CREATE TABLE task_relations (
    id            serial    NOT NULL,
    left_task_id  integer   NOT NULL,
    right_task_id integer   NOT NULL,
    created_at    timestamp NOT NULL DEFAULT now(),
    CONSTRAINT task_relations_pkey PRIMARY KEY (id),
    CONSTRAINT uq_task_relation UNIQUE (left_task_id, right_task_id),
    CONSTRAINT ck_task_relation_order CHECK (left_task_id < right_task_id),
    CONSTRAINT task_relations_left_task_id_fkey FOREIGN KEY (left_task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT task_relations_right_task_id_fkey FOREIGN KEY (right_task_id) REFERENCES tasks (id) ON DELETE CASCADE
);
CREATE INDEX ix_task_relations_left_task_id ON task_relations (left_task_id);
CREATE INDEX ix_task_relations_right_task_id ON task_relations (right_task_id);

INSERT INTO labels (name, color) VALUES
    ('Backend', NULL), ('Frontend', NULL), ('DevOps', NULL), ('Design', NULL),
    ('QA', NULL), ('Documentation', NULL), ('Bug', NULL), ('Feature', NULL),
    ('Enhancement', NULL)
ON CONFLICT DO NOTHING;
