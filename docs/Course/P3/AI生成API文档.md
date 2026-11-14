    AI生成的API文档：
    openapi: 3.0.0
    info:
    title: 校园互助服务平台 API
    description: |
    校园互助服务平台核心接口文档
    - 支持用户注册、登录、需求发布、接单、评价等核心功能
      - 使用 JWT Token 进行身份认证
      - 所有时间字段遵循 ISO 8601 格式
      version: 1.0.0
      contact:
      name: 校园互助平台团队
      email: support@campus-help.com
      license:
      name: 内部使用
    
    servers:
    - url: https://api.campus-help.com/v1
      description: 生产环境
      - url: https://sandbox-api.campus-help.com/v1
        description: 沙箱环境
    
    tags:
    - name: 认证
      description: 用户注册、登录、Token刷新
      - name: 需求
        description: 需求的发布、查询、接单等操作
      - name: 订单
        description: 订单详情、评价等操作
    
    components:
    securitySchemes:
    bearerAuth:
    type: http
    scheme: bearer
    bearerFormat: JWT
    description: 使用登录获取的JWT Token
    
    schemas:
    # ========== 通用响应结构 ==========
    SuccessResponse:
    type: object
    required:
    - code
      - message
      - data
      properties:
      code:
      type: integer
      example: 200
      message:
      type: string
      example: success
      data:
      type: object
    
          ErrorResponse:
            type: object
            required:
              - code
              - message
            properties:
              code:
                type: integer
                description: 错误码
                example: 400
              message:
                type: string
                description: 错误描述
                example: 请求参数错误
              error:
                type: string
                description: 详细错误信息
                example: "student_id: 学号格式不正确"
    
          # ========== 用户相关 ==========
          UserRegisterRequest:
            type: object
            required:
              - student_id
              - phone
              - password
              - nickname
            properties:
              student_id:
                type: string
                description: 学号
                minLength: 10
                maxLength: 12
                pattern: '^[0-9]{10,12}$'
                example: "20240001"
              phone:
                type: string
                description: 手机号
                pattern: '^1[3-9]\d{9}$'
                example: "13812345678"
              password:
                type: string
                description: 密码（8-20位，包含字母和数字）
                minLength: 8
                maxLength: 20
                pattern: '^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$'
                example: "password123"
              nickname:
                type: string
                description: 昵称
                minLength: 2
                maxLength: 20
                example: "张三"
              college:
                type: string
                description: 学院
                maxLength: 50
                example: "计算机学院"
              real_name:
                type: string
                description: 真实姓名
                minLength: 2
                maxLength: 10
                pattern: '^[\u4e00-\u9fa5]{2,10}$'
                example: "张小明"
    
          UserLoginRequest:
            type: object
            required:
              - account
              - password
            properties:
              account:
                type: string
                description: 学号或手机号
                example: "20240001"
              password:
                type: string
                description: 密码
                example: "password123"
              device_id:
                type: string
                description: 设备标识（用于多端登录管理）
                example: "device_abc123"
    
          AuthResponse:
            type: object
            properties:
              user_id:
                type: string
                example: "123456"
              nickname:
                type: string
                example: "张三"
              avatar:
                type: string
                format: uri
                nullable: true
                example: "https://cdn.campus-help.com/avatar/123456.jpg"
              token:
                type: string
                description: JWT Token
                example: "eyJhbGciOiJIUzI1NiIs..."
              expires_in:
                type: integer
                description: Token有效期（秒）
                example: 7200
    
          RefreshTokenRequest:
            type: object
            required:
              - refresh_token
            properties:
              refresh_token:
                type: string
                description: 刷新Token
                example: "eyJhbGciOiJIUzI1NiIs..."
    
          # ========== 需求相关 ==========
          DemandPublishRequest:
            type: object
            required:
              - title
              - description
              - category
              - deadline
            properties:
              title:
                type: string
                description: 需求标题
                minLength: 5
                maxLength: 50
                example: "帮忙取快递"
              description:
                type: string
                description: 需求描述
                minLength: 10
                maxLength: 500
                example: "韵达快递，包裹比较大，需要两个人搬"
              category:
                type: string
                description: 需求分类
                enum:
                  - delivery
                  - study
                  - errand
                  - repair
                  - other
                example: "delivery"
              reward:
                type: integer
                description: 报酬积分
                minimum: 0
                maximum: 1000
                default: 0
                example: 10
              deadline:
                type: string
                format: date-time
                description: 截止时间（ISO 8601）
                example: "2024-01-15T18:00:00Z"
              location:
                type: string
                description: 地点
                maxLength: 100
                example: "3号宿舍楼下"
              images:
                type: array
                description: 图片URL列表
                maxItems: 9
                items:
                  type: string
                  format: uri
                example:
                  - "https://cdn.campus-help.com/demand/dem_001_1.jpg"
                  - "https://cdn.campus-help.com/demand/dem_001_2.jpg"
    
          DemandPublishResponse:
            type: object
            properties:
              demand_id:
                type: string
                example: "dem_20240001"
              status:
                type: string
                enum: [pending, accepted, completed, cancelled]
                example: "pending"
              created_at:
                type: string
                format: date-time
                example: "2024-01-15T10:30:00Z"
    
          DemandListQuery:
            type: object
            properties:
              category:
                type: string
                enum: [delivery, study, errand, repair, other]
                description: 分类筛选
              status:
                type: string
                enum: [pending, accepted, completed]
                description: 状态筛选
              page:
                type: integer
                minimum: 1
                default: 1
                description: 页码
              page_size:
                type: integer
                minimum: 1
                maximum: 50
                default: 20
                description: 每页数量
              sort:
                type: string
                enum: [time_desc, reward_desc]
                default: time_desc
                description: 排序方式
              keyword:
                type: string
                description: 关键词搜索
                example: "快递"
              nearby:
                type: boolean
                default: false
                description: 是否按距离排序（需要位置权限）
    
          DemandListResponse:
            type: object
            properties:
              items:
                type: array
                items:
                  type: object
                  properties:
                    demand_id:
                      type: string
                      example: "dem_20240001"
                    title:
                      type: string
                      example: "帮忙取快递"
                    description:
                      type: string
                      example: "韵达快递，包裹比较大"
                    category:
                      type: string
                      enum: [delivery, study, errand, repair, other]
                      example: "delivery"
                    reward:
                      type: integer
                      example: 10
                    status:
                      type: string
                      enum: [pending, accepted, completed, cancelled]
                      example: "pending"
                    location:
                      type: string
                      nullable: true
                      example: "3号宿舍楼下"
                    distance:
                      type: number
                      description: 距离（千米）
                      example: 0.5
                    publisher:
                      type: object
                      properties:
                        user_id:
                          type: string
                          example: "123456"
                        nickname:
                          type: string
                          example: "张三"
                        avatar:
                          type: string
                          nullable: true
                          example: "https://..."
                    created_at:
                      type: string
                      format: date-time
                      example: "2024-01-15T10:30:00Z"
                    deadline:
                      type: string
                      format: date-time
                      example: "2024-01-15T18:00:00Z"
              pagination:
                type: object
                properties:
                  page:
                    type: integer
                    example: 1
                  page_size:
                    type: integer
                    example: 20
                  total:
                    type: integer
                    example: 45
                  total_pages:
                    type: integer
                    example: 3
    
          AcceptDemandRequest:
            type: object
            properties:
              message:
                type: string
                description: 接单留言
                maxLength: 100
                example: "我在附近，可以帮忙"
    
          AcceptDemandResponse:
            type: object
            properties:
              demand_id:
                type: string
                example: "dem_20240001"
              status:
                type: string
                enum: [accepted]
                example: "accepted"
              helper_id:
                type: string
                example: "789012"
              accepted_at:
                type: string
                format: date-time
                example: "2024-01-15T10:35:00Z"
    
          # ========== 订单相关 ==========
          OrderDetailResponse:
            type: object
            properties:
              order_id:
                type: string
                example: "ord_20240001"
              demand:
                type: object
                properties:
                  demand_id:
                    type: string
                    example: "dem_20240001"
                  title:
                    type: string
                    example: "帮忙取快递"
                  description:
                    type: string
                    example: "韵达快递，包裹比较大"
                  category:
                    type: string
                    enum: [delivery, study, errand, repair, other]
                    example: "delivery"
                  reward:
                    type: integer
                    example: 10
                  deadline:
                    type: string
                    format: date-time
                    example: "2024-01-15T18:00:00Z"
              publisher:
                type: object
                properties:
                  user_id:
                    type: string
                    example: "123456"
                  nickname:
                    type: string
                    example: "张三"
                  avatar:
                    type: string
                    nullable: true
                    example: "https://..."
                  phone:
                    type: string
                    description: 脱敏手机号
                    example: "138****0000"
              helper:
                type: object
                properties:
                  user_id:
                    type: string
                    example: "789012"
                  nickname:
                    type: string
                    example: "李四"
                  avatar:
                    type: string
                    nullable: true
                    example: "https://..."
                  phone:
                    type: string
                    description: 脱敏手机号
                    example: "139****0000"
              status:
                type: string
                enum: [pending, accepted, completed, cancelled, evaluated]
                example: "accepted"
              status_history:
                type: array
                items:
                  type: object
                  properties:
                    status:
                      type: string
                      example: "pending"
                    time:
                      type: string
                      format: date-time
                      example: "2024-01-15T10:30:00Z"
              evaluation:
                type: object
                nullable: true
                properties:
                  rating:
                    type: integer
                    minimum: 1
                    maximum: 5
                    example: 5
                  content:
                    type: string
                    example: "非常准时"
              created_at:
                type: string
                format: date-time
                example: "2024-01-15T10:30:00Z"
              accepted_at:
                type: string
                format: date-time
                nullable: true
                example: "2024-01-15T10:35:00Z"
              completed_at:
                type: string
                format: date-time
                nullable: true
                example: null
    
          EvaluationRequest:
            type: object
            required:
              - rating
            properties:
              rating:
                type: integer
                description: 评分
                minimum: 1
                maximum: 5
                example: 5
              content:
                type: string
                description: 评价内容
                minLength: 10
                maxLength: 200
                example: "非常准时，态度很好！"
              tags:
                type: array
                description: 评价标签
                items:
                  type: string
                  enum:
                    - on_time
                    - kind
                    - professional
                    - recommend
                example: ["on_time", "kind"]
    
          EvaluationResponse:
            type: object
            properties:
              evaluation_id:
                type: string
                example: "eval_20240001"
              rating:
                type: integer
                example: 5
              content:
                type: string
                example: "非常准时，态度很好！"
              tags:
                type: array
                items:
                  type: string
                example: ["on_time", "kind"]
              created_at:
                type: string
                format: date-time
                example: "2024-01-15T11:00:00Z"
    
          # ========== 错误码 ==========
          ErrorCode:
            type: object
            properties:
              code:
                type: integer
                enum:
                  - 200
                  - 400
                  - 401
                  - 403
                  - 404
                  - 409
                  - 422
                  - 429
                  - 500
    
    parameters:
    DemandIdParam:
    name: demand_id
    in: path
    required: true
    schema:
    type: string
    description: 需求ID
    example: "dem_20240001"
    
        OrderIdParam:
          name: order_id
          in: path
          required: true
          schema:
            type: string
          description: 订单ID
          example: "ord_20240001"
    
    responses:
    UnauthorizedError:
    description: 未认证或Token失效
    content:
    application/json:
    schema:
    $ref: '#/components/schemas/ErrorResponse'
    example:
    code: 401
    message: 未认证，请先登录
    error: "Token已过期"
    
        NotFoundError:
          description: 资源不存在
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
              example:
                code: 404
                message: 资源不存在
                error: "需求ID dem_20240999 不存在"
    
    paths:
    # ==================== 认证模块 ====================
    /auth/register:
    post:
    tags:
    - 认证
    summary: 用户注册
    description: 创建新用户账号
    operationId: register
    requestBody:
    required: true
    content:
    application/json:
    schema:
    $ref: '#/components/schemas/UserRegisterRequest'
    responses:
    '200':
    description: 注册成功
    content:
    application/json:
    schema:
    allOf:
      - $ref: '#/components/schemas/SuccessResponse'
      - type: object
      properties:
      data:
      $ref: '#/components/schemas/AuthResponse'
      '400':
      description: 参数格式错误
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
      '409':
      description: 学号或手机号已注册
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
      example:
      code: 409
      message: 账号冲突
      error: "学号 20240001 已注册"
      '422':
      description: 参数校验失败
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
    
    /auth/login:
    post:
    tags:
    - 认证
    summary: 用户登录
    description: 使用学号/手机号和密码登录
    operationId: login
    requestBody:
    required: true
    content:
    application/json:
    schema:
    $ref: '#/components/schemas/UserLoginRequest'
    responses:
    '200':
    description: 登录成功
    content:
    application/json:
    schema:
    allOf:
      - $ref: '#/components/schemas/SuccessResponse'
      - type: object
      properties:
      data:
      $ref: '#/components/schemas/AuthResponse'
      '400':
      description: 参数缺失
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
      '401':
      description: 账号或密码错误
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
      example:
      code: 401
      message: 认证失败
      error: "账号或密码错误"
      '429':
      description: 登录尝试次数过多
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
    
    /auth/refresh:
    post:
    tags:
    - 认证
    summary: 刷新Token
    description: 使用RefreshToken获取新的AccessToken
    operationId: refreshToken
    requestBody:
    required: true
    content:
    application/json:
    schema:
    $ref: '#/components/schemas/RefreshTokenRequest'
    responses:
    '200':
    description: 刷新成功
    content:
    application/json:
    schema:
    allOf:
      - $ref: '#/components/schemas/SuccessResponse'
      - type: object
      properties:
      data:
      type: object
      properties:
      token:
      type: string
      expires_in:
      type: integer
      '401':
      description: RefreshToken无效或过期
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
    
    # ==================== 需求模块 ====================
    /demands:
    post:
    tags:
    - 需求
    summary: 发布需求
    description: 发布新的互助需求
    operationId: publishDemand
    security:
      - bearerAuth: []
      requestBody:
      required: true
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/DemandPublishRequest'
      responses:
      '201':
      description: 发布成功
      content:
      application/json:
      schema:
      allOf:
      - $ref: '#/components/schemas/SuccessResponse'
      - type: object
      properties:
      data:
      $ref: '#/components/schemas/DemandPublishResponse'
      '400':
      description: 参数格式错误
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
      '401':
      $ref: '#/components/responses/UnauthorizedError'
      '422':
      description: 校验失败
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
      '429':
      description: 发布过于频繁
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
    
          get:
            tags:
              - 需求
            summary: 浏览需求列表
            description: 获取需求列表，支持多维度筛选
            operationId: getDemands
            parameters:
              - in: query
                name: category
                schema:
                  type: string
                  enum: [delivery, study, errand, repair, other]
                description: 分类筛选
              - in: query
                name: status
                schema:
                  type: string
                  enum: [pending, accepted, completed]
                description: 状态筛选
              - in: query
                name: page
                schema:
                  type: integer
                  minimum: 1
                  default: 1
                description: 页码
              - in: query
                name: page_size
                schema:
                  type: integer
                  minimum: 1
                  maximum: 50
                  default: 20
                description: 每页数量
              - in: query
                name: sort
                schema:
                  type: string
                  enum: [time_desc, reward_desc]
                  default: time_desc
                description: 排序方式
              - in: query
                name: keyword
                schema:
                  type: string
                description: 关键词搜索
              - in: query
                name: nearby
                schema:
                  type: boolean
                  default: false
                description: 按距离排序（需位置权限）
            responses:
              '200':
                description: 获取成功
                content:
                  application/json:
                    schema:
                      allOf:
                        - $ref: '#/components/schemas/SuccessResponse'
                        - type: object
                          properties:
                            data:
                              $ref: '#/components/schemas/DemandListResponse'
              '400':
                description: 参数错误
                content:
                  application/json:
                    schema:
                      $ref: '#/components/schemas/ErrorResponse'
    
    /demands/{demand_id}:
    get:
    tags:
    - 需求
    summary: 获取需求详情
    description: 查看单个需求的详细信息
    operationId: getDemandDetail
    parameters:
      - $ref: '#/components/parameters/DemandIdParam'
      responses:
      '200':
      description: 获取成功
      content:
      application/json:
      schema:
      allOf:
      - $ref: '#/components/schemas/SuccessResponse'
      - type: object
      properties:
      data:
      $ref: '#/components/schemas/OrderDetailResponse'
      '404':
      $ref: '#/components/responses/NotFoundError'
    
    /demands/{demand_id}/accept:
    post:
    tags:
    - 需求
    summary: 接单
    description: 用户接受一个待接单的需求
    operationId: acceptDemand
    security:
      - bearerAuth: []
      parameters:
      - $ref: '#/components/parameters/DemandIdParam'
      requestBody:
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/AcceptDemandRequest'
      responses:
      '200':
      description: 接单成功
      content:
      application/json:
      schema:
      allOf:
      - $ref: '#/components/schemas/SuccessResponse'
      - type: object
      properties:
      data:
      $ref: '#/components/schemas/AcceptDemandResponse'
      '400':
      description: 请求无效
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
      '401':
      $ref: '#/components/responses/UnauthorizedError'
      '403':
      description: 不能接自己的需求
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
      example:
      code: 403
      message: 无权限
      error: "不能接自己发布的需求"
      '404':
      $ref: '#/components/responses/NotFoundError'
      '409':
      description: 需求已被接走或已过期
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
      examples:
      already_accepted:
      value:
      code: 409
      message: 需求状态冲突
      error: "该需求已被其他人接走"
      expired:
      value:
      code: 409
      message: 需求状态冲突
      error: "该需求已过期"
    
    # ==================== 订单模块 ====================
    /orders/{order_id}:
    get:
    tags:
    - 订单
    summary: 查看订单详情
    description: 获取订单详细信息（仅发布者和接单者可见）
    operationId: getOrderDetail
    security:
      - bearerAuth: []
      parameters:
      - $ref: '#/components/parameters/OrderIdParam'
      responses:
      '200':
      description: 获取成功
      content:
      application/json:
      schema:
      allOf:
      - $ref: '#/components/schemas/SuccessResponse'
      - type: object
      properties:
      data:
      $ref: '#/components/schemas/OrderDetailResponse'
      '401':
      $ref: '#/components/responses/UnauthorizedError'
      '403':
      description: 无权查看
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
      example:
      code: 403
      message: 无权限访问
      error: "你不是该订单的发布者或接单者"
      '404':
      $ref: '#/components/responses/NotFoundError'
    
    /orders/{order_id}/evaluation:
    post:
    tags:
    - 订单
    summary: 提交评价
    description: 订单完成后，发布者对帮助者进行评价
    operationId: submitEvaluation
    security:
      - bearerAuth: []
      parameters:
      - $ref: '#/components/parameters/OrderIdParam'
      requestBody:
      required: true
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/EvaluationRequest'
      responses:
      '201':
      description: 评价成功
      content:
      application/json:
      schema:
      allOf:
      - $ref: '#/components/schemas/SuccessResponse'
      - type: object
      properties:
      data:
      $ref: '#/components/schemas/EvaluationResponse'
      '400':
      description: 参数错误
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
      '401':
      $ref: '#/components/responses/UnauthorizedError'
      '403':
      description: 无权评价
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
      example:
      code: 403
      message: 无权限评价
      error: "只有发布者可以评价该订单"
      '404':
      $ref: '#/components/responses/NotFoundError'
      '409':
      description: 订单未完成或已评价
      content:
      application/json:
      schema:
      $ref: '#/components/schemas/ErrorResponse'
      examples:
      not_completed:
      value:
      code: 409
      message: 评价状态冲突
      error: "订单未完成，无法评价"
      already_evaluated:
      value:
      code: 409
      message: 评价状态冲突
      error: "该订单已被评价"
    
    /orders/{order_id}/evaluation:
    get:
    tags:
    - 订单
    summary: 查看评价
    description: 获取订单的评价信息
    operationId: getEvaluation
    security:
      - bearerAuth: []
      parameters:
      - $ref: '#/components/parameters/OrderIdParam'
      responses:
      '200':
      description: 获取成功
      content:
      application/json:
      schema:
      allOf:
      - $ref: '#/components/schemas/SuccessResponse'
        - type: object
        properties:
        data:
        $ref: '#/components/schemas/EvaluationResponse'
        '404':
        description: 评价不存在
        content:
        application/json:
        schema:
        $ref: '#/components/schemas/ErrorResponse'



审查问题：

    审查维度	       问题类型	                           具体问题	                          影响接口	
    接口命名一致性  命名不规范	/demands/{id}/accept 使用动词，RESTful推荐资源化命名	    接单	
    错误处理	       缺少错误码	缺少413(请求体过大)、429限流细化、500暴露堆栈信息	           全部接口
    参数校验	       校验缺失	        deadline未校验≥当前时间，可发布已过期需求	                   发布需求
    参数校验	       校验缺失	        location/message未过滤特殊字符，存在注入风险	        发布需求/接单
    参数校验	       校验缺失	        keyword未限制长度，page_size未做上限保护	                 浏览需求列表
    安全问题	       传输安全	        密码明文传输，中间人攻击可窃取	                           注册/登录
    安全问题	       敏感信息	        订单详情返回完整手机号，用户隐私泄露	                   订单详情
    接口缺失	       功能缺失	        无取消订单/确认完成接口	                                   订单模块	

