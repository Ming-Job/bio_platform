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

use bio_info_edu;

-- 1. 课程主表
CREATE TABLE IF NOT EXISTS `course`
(
    `id`            bigint auto_increment primary key,
    `title`         varchar(200)                        not null comment '课程标题',
    `description`   text                                null comment '课程详细介绍',
    `cover_image`   varchar(255)                        null comment '课程封面图URL',
    `instructor_id` bigint                              not null comment '讲师ID(关联user表)',
    `difficulty`    enum ('BEGINNER', 'INTERMEDIATE', 'ADVANCED') default 'BEGINNER' comment '难度级别',
    `status`        enum ('DRAFT', 'PUBLISHED', 'OFFLINE') default 'DRAFT' comment '课程状态',
    `sort_order`    int       default 0                 null comment '排序权重',
    `created_at`    datetime  default CURRENT_TIMESTAMP null comment '创建时间',
    `updated_at`    datetime  default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint fk_course_instructor
        foreign key (`instructor_id`) references `user` (`id`)
            on delete restrict
) comment '课程主表' charset = utf8mb4;

CREATE INDEX idx_course_status ON `course` (`status`);


INSERT INTO `course` (`title`, `description`, `cover_image`, `instructor_id`, `difficulty`, `status`, `sort_order`)
VALUES
-- 基础技能模块
('零基础 Linux 生信命令行实操', '掌握生信分析必备的 Linux 环境搭建、常用命令、Shell 脚本编写及 Conda 软件管理。', NULL, 4, 'BEGINNER', 'PUBLISHED', 1),
('R 语言与生物信息统计绘图', '从 R 语法基础到 ggplot2 高级可视化，涵盖差异分析常用统计检验方法。', NULL, 4, 'BEGINNER', 'PUBLISHED', 2),
('Python 在生物大数据处理中的应用', '学习使用 Python 处理庞大的 Fastq、Fasta 及 VCF 文件，掌握 Biopython 库的使用。', NULL, 4, 'BEGINNER', 'PUBLISHED', 3),
('生物信息学概论：从序列到结构', '理论课：了解经典算法（如比对算法）、数据库使用指南及生信发展前沿。', NULL, 5, 'BEGINNER', 'PUBLISHED', 4),

-- 转录组与单细胞模块
('RNA-Seq 标准数据分析流程 (有参)', '全流程实战：从质控、比对、定量到差异表达分析及功能富集 (GO/KEGG)。', NULL, 14, 'INTERMEDIATE', 'PUBLISHED', 5),
('单细胞转录组 Seurat 标准分析全解析', '涵盖单细胞质控、降维聚类、细胞类型鉴定及常用可视化图表绘制。', NULL, 14, 'ADVANCED', 'PUBLISHED', 6),
('单细胞高级分析：轨迹分析与细胞通讯', '深入学习 Monocle3 拟时序分析、CellChat 细胞间通讯挖掘高级技能。', NULL, 14, 'ADVANCED', 'PUBLISHED', 7),

-- 基因组与变异检测模块
('WGS 全基因组重测序变异检测', '基于 GATK4 标准流程的 SNP/InDel 变异检测、注释及群体遗传学基础分析。', NULL, 4, 'INTERMEDIATE', 'PUBLISHED', 8),
('GWAS 关联分析与群体结构挖掘', '学习表型与基因型的关联分析，掌握 Plink 软件及 Manhattan 图绘制。', NULL, 4, 'ADVANCED', 'PUBLISHED', 9),
('表观遗传学：ChIP-Seq 数据处理实战', '掌握 Peak Calling 流程、Motif 分析以及表观基因组可视化技术。', NULL, 4, 'INTERMEDIATE', 'PUBLISHED', 10),

-- 宏基因组与前沿技术
('宏基因组物种组成与功能注释', '解析微生物群落结构，学习 QIIME2 流程及 Alpha/Beta 多样性分析。', NULL, 5, 'INTERMEDIATE', 'PUBLISHED', 11),
('蛋白质结构预测与分子对接实战', '学习 AlphaFold2 原理，使用 AutoDock 进行小分子与蛋白的虚拟筛选。', NULL, 5, 'ADVANCED', 'PUBLISHED', 12),
('深度学习在基因组学中的应用', '探索神经网络如何识别 DNA 基元，学习使用 TensorFlow 处理生物序列。', NULL, 5, 'ADVANCED', 'DRAFT', 13),

-- 临床与综合模块
('TCGA 肿瘤大数据挖掘', '教你如何从公共数据库下载数据，进行生存分析、临床关联分析及模型构建。', NULL, 14, 'INTERMEDIATE', 'PUBLISHED', 14),
('CRISPR/Cas9 基因编辑脱靶效率分析', '利用生信手段评估单引物 (sgRNA) 效率及潜在脱靶位点的检测分析。', NULL, 14, 'INTERMEDIATE', 'OFFLINE', 15);


-- 2. 课时/章节表 (支持关联到具体的生信分析工具)
CREATE TABLE IF NOT EXISTS `course_lesson`
(
    `id`              bigint auto_increment primary key,
    `course_id`       bigint                              not null comment '归属课程ID',
    `chapter_name`    varchar(100)                        null comment '章节名称(如: 第一章 测序原理)',
    `title`           varchar(200)                        not null comment '课时标题',
    `content_type`    enum ('VIDEO', 'ARTICLE', 'PRACTICE', 'PPT') default 'VIDEO' comment '内容类型：视频、图文、实操',
    `content`         longtext                            null comment '图文内容(Markdown/HTML)或实操指导',
    `video_url`       varchar(500)                        null comment '视频播放地址',
    `related_tool_id` bigint                              null comment '关联的分析工具ID(便于看完教程直接去实操)',
    `is_free_preview` tinyint(1) default 0                not null comment '是否支持免费试看',
    `sort_order`      int        default 0                not null comment '课时排序',
    `created_at`      datetime   default CURRENT_TIMESTAMP null comment '创建时间',
    `updated_at`      datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint fk_lesson_course
        foreign key (`course_id`) references `course` (`id`)
            on delete cascade,
    constraint fk_lesson_tool
        foreign key (`related_tool_id`) references `analysis_tool` (`id`)
            on delete set null
) comment '课程课时/章节表' charset = utf8mb4;

CREATE INDEX idx_lesson_course ON `course_lesson` (`course_id`, `sort_order`);

INSERT INTO `course_lesson`
(`course_id`, `chapter_name`, `title`, `content_type`, `content`, `video_url`, `related_tool_id`, `is_free_preview`, `sort_order`)
VALUES
-- 课程 1：零基础 Linux 生信命令行实操 (course_id: 1)
(31, '第一章：环境搭建与快速入门', '生信服务器登录与环境配置', 'VIDEO', '本节介绍如何使用 SSH 客户端登录 Linux 服务器，并配置基础工作目录。', '/videos/linux/01_login.mp4', NULL, 1, 1),
(31, '第一章：环境搭建与快速入门', 'Conda 软件管理实战手册', 'ARTICLE', '### Conda 常用命令\n1. 创建环境：`conda create -n bio` \n2. 安装软件：`conda install fastqc`', NULL, NULL, 1, 2),
(31, '第二章：核心命令与文件处理', '常用的文本处理三剑客 (grep/sed/awk)', 'VIDEO', '深入浅出讲解如何快速提取 Fasta 文件中的序列 ID。', '/videos/linux/02_text_tools.mp4', NULL, 0, 3),

-- 课程 5：RNA-Seq 标准数据分析流程 (course_id: 5)
(35, '第一章：测序数据质量控制', 'FastQ 文件格式详解', 'ARTICLE', '详细介绍 FastQ 格式的四行含义以及质量值的计算公式。', NULL, NULL, 1, 1),
(35, '第一章：测序数据质量控制', '实操：使用 FastQC 进行质控报告生成', 'PRACTICE', '利用平台工具对原始下机数据进行全方位的质量评估。', NULL, NULL, 1, 2),
(35, '第二章：序列比对', 'Hisat2 比对算法原理', 'VIDEO', '讲解 BWT 算法及其在转录组比对中的应用。', '/videos/rnaseq/03_alignment.mp4', NULL, 0, 3),
(35, '第二章：序列比对', '实操：HISAT2 序列比对演示', 'PRACTICE', '将质控后的 reads 比对到参考基因组上，生成 SAM/BAM 文件。', NULL, NULL, 0, 4),

-- 课程 6：单细胞转录组 Seurat 标准分析 (course_id: 6)
(36, '第一章：预处理与质控', '单细胞测序数据结构 (Matrix/Features/Barcodes)', 'ARTICLE', '理解 10X Genomics 产生的输出文件夹结构及其逻辑。', NULL, NULL, 1, 1),
(36, '第一章：预处理与质控', '实操：Seurat 对象创建与线粒体过滤', 'PRACTICE', '学习如何过滤低质量细胞（双细胞、碎片细胞及高线粒体占比细胞）。', NULL, NULL, 0, 2),
(36, '第二章：降维与聚类', 'PCA 与 UMAP 的原理与选择', 'VIDEO', '讲解非线性降维算法 UMAP 为什么在单细胞可视化中如此流行。', '/videos/scrna/05_dim_reduction.mp4', NULL, 0, 3),
(36, '第二章：降维与聚类', '实操：细胞聚类与亚群命名', 'PRACTICE', '基于标记基因（Marker Genes）对不同的免疫细胞群进行注释。', NULL, NULL, 0, 4);

ALTER TABLE `course_lesson` MODIFY COLUMN `content_type` enum ('VIDEO', 'ARTICLE', 'PRACTICE', 'PPT') DEFAULT 'VIDEO';

-- 3. 用户选课/报名记录表 (记录整体学习进度)
CREATE TABLE IF NOT EXISTS `user_course_enrollment`
(
    `id`              bigint auto_increment primary key,
    `user_id`         bigint                              not null comment '用户ID',
    `course_id`       bigint                              not null comment '课程ID',
    `progress`        tinyint    default 0                not null comment '学习进度百分比(0-100)',
    `status`          enum ('LEARNING', 'COMPLETED', 'DROPPED') default 'LEARNING' comment '学习状态',
    `last_learned_at` datetime                            null comment '最后学习时间',
    `created_at`      datetime   default CURRENT_TIMESTAMP null comment '报名时间',
    `updated_at`      datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_course
        unique (`user_id`, `course_id`),
    constraint fk_enrollment_user
        foreign key (`user_id`) references `user` (`id`)
            on delete cascade,
    constraint fk_enrollment_course
        foreign key (`course_id`) references `course` (`id`)
            on delete cascade
) comment '用户选课记录与总体进度表' charset = utf8mb4;

CREATE INDEX idx_enrollment_user ON `user_course_enrollment` (`user_id`, `status`);


-- 4. 用户课时学习明细表 (用于记录视频观看到哪一秒，或文章是否读完)
CREATE TABLE IF NOT EXISTS `user_lesson_progress`
(
    `id`              bigint auto_increment primary key,
    `user_id`         bigint                              not null comment '用户ID',
    `lesson_id`       bigint                              not null comment '课时ID',
    `status`          enum ('UNSTARTED', 'LEARNING', 'COMPLETED') default 'LEARNING' comment '课时学习状态',
    `watch_seconds`   int        default 0                not null comment '视频已观看秒数(断点续播)',
    `created_at`      datetime   default CURRENT_TIMESTAMP null comment '首次学习时间',
    `updated_at`      datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '最后更新时间',
    constraint uk_user_lesson
        unique (`user_id`, `lesson_id`),
    constraint fk_progress_user
        foreign key (`user_id`) references `user` (`id`)
            on delete cascade,
    constraint fk_progress_lesson
        foreign key (`lesson_id`) references `course_lesson` (`id`)
            on delete cascade
) comment '用户课时进度明细表' charset = utf8mb4;