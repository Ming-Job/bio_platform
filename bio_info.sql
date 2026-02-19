create table if not exists analysis_tool
(
    id          bigint auto_increment
        primary key,
    tool_code   varchar(50)                          not null comment '工具代码',
    tool_name   varchar(100)                         not null comment '工具名称',
    category    varchar(50)                          not null comment '分类',
    description text                                 null comment '工具描述',
    icon        varchar(100)                         null comment '图标',
    parameters  text                                 null comment '参数配置(JSON)',
    is_active   tinyint(1) default 1                 null comment '是否启用',
    sort_order  int        default 0                 null comment '排序',
    created_at  datetime   default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at  datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint tool_code
        unique (tool_code)
);

create table if not exists user
(
    id              bigint auto_increment comment '用户ID（主键）'
        primary key,
    username        varchar(50)                                      not null comment '用户名（唯一）',
    password        varchar(32)                                      not null comment '密码（MD5加密后）',
    role            enum ('admin', 'user') default 'user'            not null comment '角色：admin-管理员，user-普通用户',
    create_time     datetime               default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time     datetime               default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    avatar          varchar(255)                                     null comment '头像地址',
    email           varchar(100)                                     null comment '邮箱',
    phone           varchar(20)                                      null comment '手机号',
    status          tinyint(1)             default 1                 not null comment '状态：0-禁用，1-启用',
    last_login_time datetime                                         null comment '最后登录时间',
    constraint uk_email
        unique (email),
    constraint uk_phone
        unique (phone),
    constraint uk_username
        unique (username) comment '用户名唯一索引'
)
    comment '用户表' charset = utf8mb4;

ALTER TABLE user
    MODIFY COLUMN role ENUM('admin', 'user') NOT NULL DEFAULT 'user';

create table if not exists project
(
    id          bigint auto_increment
        primary key,
    user_id     bigint                               not null comment '用户ID',
    name        varchar(200)                         not null comment '项目名称',
    description text                                 null comment '项目描述',
    is_archived tinyint(1) default 0                 null comment '是否归档',
    created_at  datetime   default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at  datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint project_ibfk_1
        foreign key (user_id) references user (id)
            on delete cascade
);

create index idx_project_user
    on project (user_id);

create table if not exists sequence
(
    id            bigint auto_increment
        primary key,
    user_id       bigint                                not null comment '用户ID',
    name          varchar(200)                          not null comment '序列名称',
    description   text                                  null comment '描述',
    content       text                                  not null comment '序列内容',
    file_name     varchar(255)                          null comment '文件名',
    file_size     bigint                                null comment '文件大小(字节)',
    sequence_type varchar(20) default 'DNA'             null comment '序列类型',
    length        int                                   not null comment '序列长度',
    created_at    datetime    default CURRENT_TIMESTAMP null comment '创建时间',
    constraint sequence_ibfk_1
        foreign key (user_id) references user (id)
            on delete cascade
);

create table if not exists analysis_task
(
    id             bigint auto_increment
        primary key,
    user_id        bigint                                                                       not null comment '用户ID',
    sequence_id    bigint                                                                       not null comment '序列ID',
    task_name      varchar(200)                                                                 not null comment '任务名称',
    status         enum ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED') default 'PENDING'         null comment '状态',
    tools_selected json                                                                         not null comment '选中的工具ID数组(JSON)',
    parameters     text                                                                         null comment '参数配置(JSON)',
    progress       int                                                default 0                 null comment '进度(0-100)',
    error_message  text                                                                         null comment '错误信息',
    started_at     datetime                                                                     null comment '开始时间',
    completed_at   datetime                                                                     null comment '完成时间',
    created_at     datetime                                           default CURRENT_TIMESTAMP null comment '创建时间',
    constraint analysis_task_ibfk_1
        foreign key (user_id) references user (id),
    constraint analysis_task_ibfk_2
        foreign key (sequence_id) references sequence (id)
);

create table if not exists analysis_result
(
    id          bigint auto_increment
        primary key,
    task_id     bigint                             not null comment '任务ID',
    tool_name   varchar(50)                        not null comment '工具名称',
    result_data text                               not null comment '结果数据(JSON格式)',
    created_at  datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_task_tool
        unique (task_id, tool_name),
    constraint analysis_result_ibfk_1
        foreign key (task_id) references analysis_task (id)
            on delete cascade
);

create index idx_result_task
    on analysis_result (task_id);

create index idx_task_created
    on analysis_task (created_at desc);

create index idx_task_sequence
    on analysis_task (sequence_id);

create index idx_task_status
    on analysis_task (status);

create index idx_task_user
    on analysis_task (user_id);

create index idx_task_user_status
    on analysis_task (user_id, status);

create table if not exists project_task
(
    id         bigint auto_increment
        primary key,
    project_id bigint                             not null comment '项目ID',
    task_id    bigint                             not null comment '任务ID',
    created_at datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_project_task
        unique (project_id, task_id),
    constraint project_task_ibfk_1
        foreign key (project_id) references project (id)
            on delete cascade,
    constraint project_task_ibfk_2
        foreign key (task_id) references analysis_task (id)
            on delete cascade
);

create index task_id
    on project_task (task_id);

create index idx_sequence_created
    on sequence (created_at desc);

create index idx_sequence_user
    on sequence (user_id);

create table if not exists task_tool
(
    id          bigint auto_increment
        primary key,
    task_id     bigint                                                                       not null comment '任务ID',
    tool_id     bigint                                                                       not null comment '工具ID',
    parameters  text                                                                         null comment '该任务中该工具的特定参数(JSON)',
    status      enum ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED') default 'PENDING'         null comment '该工具的执行状态',
    result_data text                                                                         null comment '该工具的结果数据(JSON)',
    created_at  datetime                                           default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at  datetime                                           default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_task_tool
        unique (task_id, tool_id),
    constraint task_tool_ibfk_1
        foreign key (task_id) references analysis_task (id)
            on delete cascade,
    constraint task_tool_ibfk_2
        foreign key (tool_id) references analysis_tool (id)
);

create index idx_task_tool_task
    on task_tool (task_id);

create index idx_task_tool_tool
    on task_tool (tool_id);

create index idx_last_login
    on user (last_login_time);

create index idx_role_status
    on user (role, status);

create index idx_user_email
    on user (email);

create index idx_user_status
    on user (status);


# 0125/2026
-- 文件表（简化版）
CREATE TABLE IF NOT EXISTS `bio_files` (
                                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID',
                                           `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
                                           `stored_name` varchar(255) NOT NULL COMMENT '存储文件名(UUID)',
                                           `file_type` varchar(50) NOT NULL COMMENT '文件类型: fastq, fasta, bam等',
                                           `file_ext` varchar(20) NOT NULL COMMENT '文件扩展名',
                                           `size_bytes` bigint NOT NULL COMMENT '文件大小(字节)',
                                           `storage_path` varchar(500) NOT NULL COMMENT '相对存储路径',
                                           `md5_hash` varchar(32) NOT NULL COMMENT 'MD5哈希值',
                                           `user_id` bigint NOT NULL COMMENT '上传用户ID',
                                           `project_id` bigint DEFAULT NULL COMMENT '所属项目ID',
                                           `status` varchar(20) NOT NULL DEFAULT 'uploading' COMMENT '文件状态: uploading/uploaded/ready/deleted',
                                           `upload_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
                                           `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                           `description` text COMMENT '文件描述',
                                           PRIMARY KEY (`id`),
                                           UNIQUE KEY `uk_stored_name` (`stored_name`),
                                           KEY `idx_user_id` (`user_id`),
                                           KEY `idx_project_id` (`project_id`),
                                           KEY `idx_status` (`status`),
                                           KEY `idx_upload_time` (`upload_time`),
                                           KEY `idx_md5_hash` (`md5_hash`) COMMENT '用于去重'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件表';

-- 文件上传记录表（简化版）
CREATE TABLE IF NOT EXISTS `bio_file_uploads` (
                                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '上传记录ID',
                                                  `file_id` bigint DEFAULT NULL COMMENT '关联的文件ID',
                                                  `user_id` bigint NOT NULL COMMENT '关联的用户ID',
                                                  `upload_session` varchar(64) NOT NULL COMMENT '上传会话ID',
                                                  `upload_method` varchar(20) DEFAULT 'direct' COMMENT '上传方式: direct/chunked',
                                                  `chunked` tinyint(1) DEFAULT '0' COMMENT '是否分片上传',
                                                  `total_chunks` int DEFAULT '0' COMMENT '总分片数',
                                                  `uploaded_chunks` int DEFAULT '0' COMMENT '已上传分片数',
                                                  `bytes_uploaded` bigint DEFAULT '0' COMMENT '已上传字节数',
                                                  `bytes_total` bigint NOT NULL COMMENT '总字节数',
                                                  `status` varchar(20) NOT NULL DEFAULT 'initializing' COMMENT '上传状态: initializing/uploading/completed/failed/cancelled',
                                                  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
                                                  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
                                                  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',
                                                  PRIMARY KEY (`id`),
                                                  UNIQUE KEY `uk_upload_session` (`upload_session`),
                                                  KEY `idx_user_id` (`user_id`),
                                                  KEY `idx_file_id` (`file_id`),
                                                  KEY `idx_status` (`status`),
                                                  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件上传记录表';

-- 文件元数据表（简化版）
CREATE TABLE IF NOT EXISTS `bio_file_metadata` (
                                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '元数据ID',
                                                   `file_id` bigint NOT NULL COMMENT '关联的文件ID',
                                                   `sample_id` varchar(100) DEFAULT NULL COMMENT '样本ID',
                                                   `sample_name` varchar(200) DEFAULT NULL COMMENT '样本名称',
                                                   `organism` varchar(100) DEFAULT NULL COMMENT '生物体',
                                                   `experiment_type` varchar(50) DEFAULT NULL COMMENT '实验类型: RNA-Seq, WGS, ChIP-Seq等',
                                                   `paired_end` tinyint(1) DEFAULT '0' COMMENT '是否双端测序',
                                                   `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                   `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                   PRIMARY KEY (`id`),
                                                   UNIQUE KEY `uk_file_id` (`file_id`),
                                                   KEY `idx_sample_id` (`sample_id`),
                                                   KEY `idx_organism` (`organism`),
                                                   KEY `idx_experiment_type` (`experiment_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件元数据扩展表';

-- 添加外键约束（如果需要）
ALTER TABLE `bio_files`
    ADD CONSTRAINT `fk_files_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE `bio_file_uploads`
    ADD CONSTRAINT `fk_uploads_file` FOREIGN KEY (`file_id`) REFERENCES `bio_files` (`id`) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE `bio_file_metadata`
    ADD CONSTRAINT `fk_metadata_file` FOREIGN KEY (`file_id`) REFERENCES `bio_files` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- 插入示例项目（假设用户ID为1）
INSERT INTO `project` (`user_id`, `name`, `description`) VALUES
                                                             (6, '肝癌转录组分析', '研究肝癌组织的转录组特征'),
                                                             (6, '小麦基因组测序', '小麦全基因组测序项目');

-- 插入示例文件（假设项目ID为1）
INSERT INTO `bio_files` (`original_name`, `stored_name`, `file_type`, `file_ext`, `size_bytes`, `storage_path`, `md5_hash`, `user_id`, `project_id`, `status`, `description`) VALUES
                                                                                                                                                                                  ('sample1.fastq.gz', 'f47ac10b-58cc-4372-a567-0e02b2c3d479.fastq.gz', 'fastq', '.fastq.gz', 1073741824, 'user1/2024/01/01/f47ac10b-58cc-4372-a567-0e02b2c3d479.fastq.gz', 'e4d909c290d0fb1ca068ffaddf22cbd0', 1, 1, 'ready', '肝癌样本1的测序数据'),
                                                                                                                                                                                  ('sample2.fastq.gz', '550e8400-e29b-41d4-a716-446655440000.fastq.gz', 'fastq', '.fastq.gz', 536870912, 'user1/2024/01/01/550e8400-e29b-41d4-a716-446655440000.fastq.gz', 'd41d8cd98f00b204e9800998ecf8427e', 1, 1, 'ready', '肝癌样本2的测序数据');

-- 插入示例元数据
INSERT INTO `bio_file_metadata` (`file_id`, `sample_id`, `sample_name`, `organism`, `experiment_type`, `paired_end`) VALUES
                                                                                                                         (3, 'S001', '肝癌组织样本1', 'Homo sapiens', 'RNA-Seq', 1),
                                                                                                                         (4, 'S002', '肝癌组织样本2', 'Homo sapiens', 'RNA-Seq', 1);
-- 为常用查询字段添加索引
CREATE INDEX `idx_files_user_project` ON `bio_files` (`user_id`, `project_id`);
CREATE INDEX `idx_files_type_status` ON `bio_files` (`file_type`, `status`);
CREATE INDEX `idx_uploads_user_status` ON `bio_file_uploads` (`user_id`, `status`);