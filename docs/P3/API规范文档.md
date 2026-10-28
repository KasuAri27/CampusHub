校园互助服务平台API规范文档
1.	通用规范

      URL路径：https://api.campus-help.com/v1
      
      字符编码：UTF-8
      
      认证方式：在请求头中携带 Authorization: Bearer <token>
      
      通用响应结构： 
       ①成功响应：


       {
       "code": 200,
       "message": "success",
       "data": { ... }
       }

②失败响应：
      
      {
      "code": 400,
      "message": "错误描述",
      "error": "详细错误信息"
      }

通用错误码：

      错误码	说明
      200	成功
      400	请求参数错误
      401	未认证/Token失效
      403	无权限
      404	资源不存在
      409	资源冲突
      422	参数校验失败
      429	请求过于频繁
      500	服务器内部错误
2.	接口详细定义

      2.1	用户注册

 
      项目	      说明
      URL	      /auth/register
      HTTP方法	  POST
      是否需要认证  否


    参数名	        类型	是否必填
    Student_id	String	是
    Phone	        String	是
    Password	String	是
    Nickname	String	是
    College	        String	否
    Real_name	string	否

响应格式：
①	成功响应：
      
    {
    "code": 200,
    "message": "success",
    "data": {
    "user_id": "123456",
    " student_id": "20240001",
    "nickname": "张三",
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "expires_in": 7200
    }

②	失败响应：

    错误码	场景
    400	参数格式错误
    409	学号/手机号已注册
    422	参数校验失败

2.2	用户登录

    项目	说明
    URL	/auth/login
    HTTP方法	POST
    是否需要认证	否
请求参数：

    参数名	        类型	必填	说明
    Account   	String	是	学号/手机号
    password	string	是	密码

①	成功响应：

    {
    "code": 200,
    "message": "success",
    "data": {
    "user_id": "123456",
    "nickname": "张三",
    "avatar": "https://...",
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "expires_in": 7200
    }
②	失败响应：

       错误码	场景
       400	      参数缺失
       401	    账号或密码错误
       429	    登录尝试次数过多

2.3	发布需求

    项目	说明
    URL	/demands
    HTTP方法	POST
    是否需要认证	是
请求参数：

     参数名	        类型	必填	说明
     Title	        string	是	需求标题
     Description	String	是	需求描述
      Category	String	是	分类
      Reward	Integer	否	报酬
      Deadline	String	是	截止时间
      Location	String	否	地点
      images	Array	否	图片URL列表
 Category枚举值：①二手交易 ②学习辅导 ③跑腿办事 ④咨询求助 ⑤其他

①	成功响应：

    {
    "code": 200,
    "message": "success",
    "data": {
    "demand_id": "dem_20240001",
    "status": "pending",
    "created_at": "2024-01-15T10:30:00Z"
    }

②	失败响应

    错误码	场景
    400	参数格式错误
    401	未认证
    422	校验失败
    429	发布过于频繁

2.4	浏览需求列表

    项目	说明
    URL	/demands
    HTTP方法	GET
    是否需要认证	否
请求参数：

    参数名	       类型	必填	说明
    Category	string	否	分类筛选
    Status	        String	否	状态筛选
    Page	        Integer	否	页码
    Page_size	Integer	否	每页数量
    Sort	        String	否	排列方式
    Keyword	        String	否	关键词搜索
    nearby	        Boolean	否	是否按距离排序
①	成功响应：

    {
     "code": 200,
      "message": "success",
      "data": {
       "items": [
      {
       "demand_id": "dem_20240001",
         "title": "帮忙取快递",
             "description": "韵达快递，包裹比较大",
         "category": "delivery",
         "reward": 10,
           "status": "pending",
        "location": "3号宿舍楼下",
        "distance": 0.5,
        "publisher": {
         "user_id": "123456",
          "nickname": "张三",
         "avatar": "https://..."
        },
       "created_at": "2024-01-15T10:30:00Z",
        "deadline": "2024-01-15T18:00:00Z"
        }
        ],
       "pagination": {
       "page": 1,
        "page_size": 20,
       "total": 45,
       "total_pages": 3
        }
        }
        }

2.5	接单

      项目	        说明
      URL	/demands/{demand_id}/accept
     HTTP方法	POST
    是否需要认证	是
请求参数：

      参数名	        类型	必填	说明
     Demand_id	string	是	需求ID
       message	String	否	接单留言，最多200字符
①	成功响应：

      {
      "code": 200,
      "message": "success",
      "data": {
      "demand_id": "dem_20240001",
      "status": "accepted",
      "helper_id": "789012",
      "accepted_at": "2024-01-15T10:35:00Z"
      }
      }
②	失败响应：

      错误码	场景
      400	请求无效
      401	未认证
      403	不能接自己的需求
      404	需求不存在
      409	需求已被他人接走
      409	需求已过期或已完成

2.6	查看接单详情

      项目	         说明
      URL	/orders/{order_id}
      HTTP方法	GET
      是否需要认证	是（仅发布者和接单者可见）
请求参数：

      参数名	          类型	必填	说明
      Order_id	String	是	订单ID
①	成功响应：

      {
      "code": 200,
      "message": "success",
      "data": {
      "order_id": "ord_20240001",
      "demand": {
      "demand_id": "dem_20240001",
      "title": "帮忙取快递",
      "description": "韵达快递，包裹比较大",
      "category": "delivery",
      "reward": 10,
      "deadline": "2024-01-15T18:00:00Z"
      },
      "publisher": {
      "user_id": "123456",
      "nickname": "张三",
      "avatar": "https://...",
      "phone": "138****0000"
      },
      "helper": {
      "user_id": "789012",
      "nickname": "李四",
      "avatar": "https://...",
      "phone": "139****0000"
      },
      "status": "accepted",
      "status_history": [
      { "status": "pending", "time": "2024-01-15T10:30:00Z" },
      { "status": "accepted", "time": "2024-01-15T10:35:00Z" }
      ],
      "evaluation": null,
      "created_at": "2024-01-15T10:30:00Z",
      "accepted_at": "2024-01-15T10:35:00Z",
      "completed_at": null
      }
      }
②	失败响应：

      错误码	场景
      401	未认证
      403	无权查看该订单
      404	订单不存在

2.7	提交评价

      项目	        说明
      URL	/orders/{order_id}/evaluation
      HTTP方法	POST
      是否需要认证	是（仅订单参与方）

请求参数：

      参数名	        类型	必填	说明
      Order_id	string	是	订单ID
      Rating	integer	是	评分
      Content	String	否	评价内容
      tags	         Array	否	标签
①	成功响应：

      {
      "code": 200,
      "message": "success",
      "data": {
      "evaluation_id": "eval_20240001",
      "rating": 5,
      "content": "非常准时，态度很好！",
      "tags": ["on_time", "kind"],
      "created_at": "2024-01-15T11:00:00Z"
      }
      }
②	失败响应：

      错误码	场景
      400	参数错误
      401	未认证
      403	无权评价该订单
      404	订单不存在
      409	订单未完成，无法评价
      409	已评价过

