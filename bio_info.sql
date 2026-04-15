create table analysis_pipeline
(
    id               bigint auto_increment
        primary key,
    pipeline_code    varchar(50)                           not null comment '流程唯一编码',
    name             varchar(100)                          not null comment '流程名称',
    description      varchar(500)                          null comment '流程描述',
    default_params   json                                  null comment '默认参数',
    sort_order       int         default 0                 null comment '排序',
    is_active        tinyint(1)  default 1                 null comment '是否启用',
    created_at       datetime    default CURRENT_TIMESTAMP null,
    updated_at       datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    category         varchar(50) default 'genomics'        null comment '所属领域',
    ref_fa_file_id   bigint                                null comment '绑定的参考基因组文件ID',
    ref_gtf_file_id  bigint                                null comment '绑定的注释文件ID',
    ref_seqs_file_id bigint                                null comment '绑定的参考序列库文件ID (如 Silva seqs.qza)',
    ref_tax_file_id  bigint                                null comment '绑定的参考物种层级库文件ID (如 Silva tax.qza)',
    constraint uk_pipeline_code
        unique (pipeline_code)
)
    comment '分析流程模板表';

create table bio_case
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    title             varchar(128)                          not null comment '案例标题',
    category          varchar(32)                           not null comment '所属引擎模块(copilot/pipeline/structure/template)',
    tags              varchar(128)                          null comment '标签(逗号分隔，如：差异分析,Python)',
    difficulty        varchar(16) default 'medium'          null comment '难度(easy/medium/hard)',
    dataset           varchar(64)                           null comment '关联的数据集文件名',
    prompt            text                                  null comment '预设的AI提示词',
    description       varchar(256)                          null comment '列表页简短描述',
    content           longtext                              null comment '详情页的长篇图文/Markdown内容',
    image_url         varchar(256)                          null comment '封面图URL(用于列表展示)',
    result_image_url  varchar(256)                          null comment '预期产出/示例结果图URL(用于详情页展示)',
    result_image_desc text                                  null comment '预期产出结果图的专业解读',
    forks             int         default 0                 null comment '提取/载入次数',
    create_time       datetime    default CURRENT_TIMESTAMP null comment '创建时间',
    update_time       datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '生信案例矩阵表';

create table task_diff_expression
(
    id               bigint auto_increment comment '主键ID'
        primary key,
    control_task_id  bigint                             not null comment '对照组任务ID',
    treat_task_id    bigint                             not null comment '处理组任务ID',
    gene_id          varchar(64)                        not null comment '基因唯一标识',
    control_count    int                                not null comment '对照组原始表达量',
    treat_count      int                                not null comment '处理组原始表达量',
    log2_fold_change double                             not null comment '真实计算出的差异倍数(log2FC)',
    p_value          double                             not null comment '显著性(单样本对比固定为1.0)',
    create_time      datetime default CURRENT_TIMESTAMP null comment '计算时间'
)
    comment '任务差异分析结果表';

create index idx_task_pair
    on task_diff_expression (control_task_id, treat_task_id);

create table task_gene_expression
(
    id         bigint auto_increment
        primary key,
    task_id    bigint                             not null comment '关联的任务ID',
    gene_id    varchar(64)                        not null comment '基因名',
    read_count int                                not null comment '表达量(Count)',
    created_at datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '任务基因表达量结果表';

create index idx_task_id
    on task_gene_expression (task_id);

create table task_gwas_result
(
    id         bigint auto_increment comment '主键ID'
        primary key,
    task_id    bigint                             not null comment '关联的任务ID',
    chr        varchar(10)                        not null comment '染色体编号 (CHR)',
    snp        varchar(100)                       not null comment '位点名称 (SNP)',
    bp         bigint                             not null comment '物理位置 (BP)',
    p_value    double                             not null comment '显著性P值 (P)',
    created_at datetime default CURRENT_TIMESTAMP null comment '创建时间',
    ref_allele varchar(50)                        null comment '参考等位基因 (allele0)',
    alt_allele varchar(50)                        null comment '突变等位基因 (allele1)',
    maf        double                             null comment '等位基因频率 (af)',
    beta       double                             null comment '效应大小 (beta)'
)
    comment 'GWAS全基因组关联分析结果表';

create index idx_task_id
    on task_gwas_result (task_id);

create table user
(
    id              bigint auto_increment comment '用户ID（主键）'
        primary key,
    username        varchar(50)                                      not null comment '用户名（唯一）',
    password        varchar(32)                                      not null comment '密码（MD5加密后）',
    role            enum ('admin', 'user') default 'user'            not null,
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
    comment '用户表';

create table analysis_task
(
    id            bigint auto_increment
        primary key,
    user_id       bigint                                                                       not null comment '用户ID',
    project_id    bigint                                                                       null comment '关联的项目ID',
    pipeline_id   bigint                                                                       null comment '关联的分析流程模板ID',
    task_name     varchar(200)                                                                 not null comment '任务名称',
    status        enum ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED') default 'PENDING'         null comment '状态',
    parameters    text                                                                         null comment '参数配置(JSON)',
    progress      int                                                default 0                 null comment '进度(0-100)',
    progress_msg  varchar(255)                                                                 null comment '进度文字(例如: 正在比对序列)',
    error_message text                                                                         null comment '错误信息',
    started_at    datetime                                                                     null comment '开始时间',
    completed_at  datetime                                                                     null comment '完成时间',
    created_at    datetime                                           default CURRENT_TIMESTAMP null comment '创建时间',
    constraint analysis_task_ibfk_1
        foreign key (user_id) references user (id),
    constraint fk_task_pipeline
        foreign key (pipeline_id) references analysis_pipeline (id)
            on delete set null
);

create index idx_task_created
    on analysis_task (created_at desc);

create index idx_task_status
    on analysis_task (status);

create index idx_task_user
    on analysis_task (user_id);

create index idx_task_user_status
    on analysis_task (user_id, status);

create table course
(
    id            bigint auto_increment
        primary key,
    title         varchar(200)                                                            not null comment '课程标题',
    description   text                                                                    null comment '课程详细介绍',
    cover_image   varchar(255)                                                            null comment '课程封面图URL',
    instructor_id bigint                                                                  not null comment '讲师ID(关联user表)',
    difficulty    enum ('BEGINNER', 'INTERMEDIATE', 'ADVANCED') default 'BEGINNER'        null comment '难度级别',
    status        enum ('DRAFT', 'PUBLISHED', 'OFFLINE')        default 'DRAFT'           null comment '课程状态',
    sort_order    int                                           default 0                 null comment '排序权重',
    created_at    datetime                                      default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at    datetime                                      default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint fk_course_instructor
        foreign key (instructor_id) references user (id)
)
    comment '课程主表';

create index idx_course_status
    on course (status);

create table course_lesson
(
    id                  bigint auto_increment
        primary key,
    course_id           bigint                                                                 not null comment '归属课程ID',
    chapter_name        varchar(100)                                                           null comment '章节名称(如: 第一章 测序原理)',
    title               varchar(200)                                                           not null comment '课时标题',
    content_type        enum ('VIDEO', 'ARTICLE', 'PRACTICE', 'PPT') default 'VIDEO'           null,
    content             longtext                                                               null comment '图文内容(Markdown/HTML)或实操指导',
    video_url           varchar(500)                                                           null comment '视频播放地址',
    related_pipeline_id bigint                                                                 null comment '关联的分析流程(Pipeline)ID',
    is_free_preview     tinyint(1)                                   default 0                 not null comment '是否支持免费试看',
    sort_order          int                                          default 0                 not null comment '课时排序',
    created_at          datetime                                     default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at          datetime                                     default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint fk_lesson_course
        foreign key (course_id) references course (id)
            on delete cascade,
    constraint fk_lesson_pipeline
        foreign key (related_pipeline_id) references analysis_pipeline (id)
            on delete set null
)
    comment '课程课时/章节表';

create index idx_lesson_course
    on course_lesson (course_id, sort_order);

create table project
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

create table bio_files
(
    id            bigint auto_increment comment '文件ID'
        primary key,
    original_name varchar(255)                          not null comment '原始文件名',
    stored_name   varchar(255)                          not null comment '存储文件名(UUID)',
    file_type     varchar(50)                           not null comment '文件类型: fastq, fasta, bam等',
    file_ext      varchar(20)                           not null comment '文件扩展名',
    size_bytes    bigint                                not null comment '文件大小(字节)',
    storage_path  varchar(500)                          not null comment '相对存储路径',
    md5_hash      varchar(32)                           not null comment 'MD5哈希值',
    user_id       bigint                                not null comment '上传用户ID',
    project_id    bigint                                null comment '所属项目ID',
    status        varchar(20) default 'uploading'       not null comment '文件状态: uploading/uploaded/ready/deleted',
    upload_time   datetime    default CURRENT_TIMESTAMP null comment '上传时间',
    update_time   datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    description   text                                  null comment '文件描述',
    file_source   varchar(20) default 'upload'          null comment '文件来源: upload-手动上传, generate-分析产出',
    constraint uk_stored_name
        unique (stored_name),
    constraint fk_files_project
        foreign key (project_id) references project (id)
            on update cascade on delete set null
)
    comment '文件表';

create table analysis_task_file
(
    id         bigint auto_increment
        primary key,
    task_id    bigint                                not null comment '分析任务ID',
    file_id    bigint                                not null comment '云端文件ID (关联 bio_files)',
    file_role  varchar(50) default 'input'           null comment '文件角色(如: input, control)',
    created_at datetime    default CURRENT_TIMESTAMP null,
    constraint fk_taskfile_file
        foreign key (file_id) references bio_files (id)
            on delete cascade,
    constraint fk_taskfile_task
        foreign key (task_id) references analysis_task (id)
            on delete cascade
)
    comment '分析任务输入文件清单';

create table bio_file_metadata
(
    id              bigint auto_increment comment '元数据ID'
        primary key,
    file_id         bigint                               not null comment '关联的文件ID',
    sample_id       varchar(100)                         null comment '样本ID',
    sample_name     varchar(200)                         null comment '样本名称',
    organism        varchar(100)                         null comment '生物体',
    experiment_type varchar(50)                          null comment '实验类型: RNA-Seq, WGS, ChIP-Seq等',
    paired_end      tinyint(1) default 0                 null comment '是否双端测序',
    created_at      datetime   default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at      datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_file_id
        unique (file_id),
    constraint fk_metadata_file
        foreign key (file_id) references bio_files (id)
            on update cascade on delete cascade
)
    comment '文件元数据扩展表';

create index idx_experiment_type
    on bio_file_metadata (experiment_type);

create index idx_organism
    on bio_file_metadata (organism);

create index idx_sample_id
    on bio_file_metadata (sample_id);

create table bio_file_uploads
(
    id              bigint auto_increment comment '上传记录ID'
        primary key,
    file_id         bigint                                null comment '关联的文件ID',
    user_id         bigint                                not null comment '关联的用户ID',
    upload_session  varchar(64)                           not null comment '上传会话ID',
    upload_method   varchar(20) default 'direct'          null comment '上传方式: direct/chunked',
    chunked         tinyint(1)  default 0                 null comment '是否分片上传',
    total_chunks    int         default 0                 null comment '总分片数',
    uploaded_chunks int         default 0                 null comment '已上传分片数',
    bytes_uploaded  bigint      default 0                 null comment '已上传字节数',
    bytes_total     bigint                                not null comment '总字节数',
    status          varchar(20) default 'initializing'    not null comment '上传状态: initializing/uploading/completed/failed/cancelled',
    start_time      datetime    default CURRENT_TIMESTAMP null comment '开始时间',
    end_time        datetime                              null comment '结束时间',
    error_message   varchar(500)                          null comment '错误信息',
    constraint uk_upload_session
        unique (upload_session),
    constraint fk_uploads_file
        foreign key (file_id) references bio_files (id)
            on update cascade on delete set null
)
    comment '文件上传记录表';

create index idx_file_id
    on bio_file_uploads (file_id);

create index idx_start_time
    on bio_file_uploads (start_time);

create index idx_status
    on bio_file_uploads (status);

create index idx_uploads_user_status
    on bio_file_uploads (user_id, status);

create index idx_user_id
    on bio_file_uploads (user_id);

create index idx_files_type_status
    on bio_files (file_type, status);

create index idx_files_user_project
    on bio_files (user_id, project_id);

create index idx_md5_hash
    on bio_files (md5_hash)
    comment '用于去重';

create index idx_project_id
    on bio_files (project_id);

create index idx_status
    on bio_files (status);

create index idx_upload_time
    on bio_files (upload_time);

create index idx_user_id
    on bio_files (user_id);

create index idx_project_user
    on project (user_id);

create table project_task
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

create index idx_last_login
    on user (last_login_time);

create index idx_role_status
    on user (role, status);

create index idx_user_email
    on user (email);

create index idx_user_status
    on user (status);

create table user_course_enrollment
(
    id              bigint auto_increment
        primary key,
    user_id         bigint                                                              not null comment '用户ID',
    course_id       bigint                                                              not null comment '课程ID',
    progress        tinyint                                   default 0                 not null comment '学习进度百分比(0-100)',
    status          enum ('LEARNING', 'COMPLETED', 'DROPPED') default 'LEARNING'        null comment '学习状态',
    last_learned_at datetime                                                            null comment '最后学习时间',
    created_at      datetime                                  default CURRENT_TIMESTAMP null comment '报名时间',
    updated_at      datetime                                  default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_course
        unique (user_id, course_id),
    constraint fk_enrollment_course
        foreign key (course_id) references course (id)
            on delete cascade,
    constraint fk_enrollment_user
        foreign key (user_id) references user (id)
            on delete cascade
)
    comment '用户选课记录与总体进度表';

create index idx_enrollment_user
    on user_course_enrollment (user_id, status);

create table user_lesson_progress
(
    id            bigint auto_increment
        primary key,
    user_id       bigint                                                                not null comment '用户ID',
    lesson_id     bigint                                                                not null comment '课时ID',
    status        enum ('UNSTARTED', 'LEARNING', 'COMPLETED') default 'LEARNING'        null comment '课时学习状态',
    watch_seconds int                                         default 0                 not null comment '视频已观看秒数(断点续播)',
    created_at    datetime                                    default CURRENT_TIMESTAMP null comment '首次学习时间',
    updated_at    datetime                                    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '最后更新时间',
    constraint uk_user_lesson
        unique (user_id, lesson_id),
    constraint fk_progress_lesson
        foreign key (lesson_id) references course_lesson (id)
            on delete cascade,
    constraint fk_progress_user
        foreign key (user_id) references user (id)
            on delete cascade
)
    comment '用户课时进度明细表';

