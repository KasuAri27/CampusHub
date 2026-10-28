
# ADR-4：测试语言选择 Java

## 状态
已接受

## 背景

MVP 三个核心模块（用户管理、需求发布与浏览、订单流程管理）需要在 10 周内完成功能测试和 API 集成测试。

后端为 Java（Spring Boot），前端为 Vue 3。需要确定测试脚本的编写语言和测试框架。候选方案为 Java（JUnit 5 + RestAssured）和 Python（pytest + requests）。

## 决策

采用 **Java + JUnit 5 + RestAssured** 作为 API 集成测试和端到端测试的技术栈。

技术栈细节：
- 测试框架：JUnit 5
- HTTP 客户端：RestAssured
- 断言增强：AssertJ / Hamcrest
- Mock 框架：Mockito
- 测试数据管理：@BeforeEach / @AfterEach + 独立的 Test Fixtures 类
- 报告：Surefire/Failsafe 插件生成 XML 报告，CI 原生支持

后端单元测试同样使用 JUnit 5 + Mockito，与集成测试共享统一的构建生命周期。

## 理由

1. **语言统一，降低维护成本**：整个项目统一使用 Java，测试代码与业务代码共享同一工具链（Maven）、同一 IDE（IntelliJ IDEA），无需额外安装 Python 运行时，开发环境一致性好
2. **构建流程一体化**：JUnit 5 测试直接在 Maven Surefire/Failsafe 插件中运行，`mvn test` / `mvn verify` 可同时执行单元测试和集成测试，无需维护两套独立的测试执行流程
3. **RestAssured 专为 REST API 设计**：提供 Given-When-Then 风格的 DSL 语法，内置 JSON/XML 解析和 Schema 校验，编写 API 测试简洁直观，与 Spring Boot 生态无缝衔接
4. **团队技术栈一致**：团队成员均具备 Java 开发经验，无需额外学习 Python 即可编写测试用例，降低认知负担，提高代码评审和协作效率
5. **为什么不选 Python/pytest**：虽然 pytest 语法简洁，但引入第二语言会增加环境配置成本（Python 依赖管理、虚拟环境），且 Maven 和 pip 两套依赖体系不易统一管理；团队成员统一使用 Java 可避免在多语言之间切换上下文

## 后果

- **正面影响**：
  - 开发环境统一：只需 JDK 17 + Maven 即可完成全部开发测试工作
  - CI 集成零配置：GitLab CI 使用 Maven 镜像即可运行所有测试，无需多语言构建环境
  - 测试覆盖率统一采集：JaCoCo 插件同时覆盖单元测试和集成测试的代码路径
- **负面影响**：
  - RestAssured DSL 的 Given-When-Then 风格比 pytest 稍显冗长，需编写更多样板代码
  - Java 的测试类结构（@BeforeEach/@AfterEach）相比 pytest fixture 机制在测试数据复用上略为繁琐
- **需要关注的风险**：
  - 测试数据准备需与后端数据库协调——建议通过 API 创建测试数据（而非直接操作数据库），保持黑盒边界
  - 订单流程测试涉及状态变更，需设计好测试用例的执行顺序和隔离策略（可用 @TestMethodOrder 或独立 Test Fixtures）

## AI 辅助记录
- AI 初稿内容摘要：原始初稿选择 Python + pytest，理由为语法简洁、与后端解耦、pytest 生态完善
- 人工修订内容：将测试语言从 Python 改为 Java（JUnit 5 + RestAssured），统一技术栈，消除多语言维护负担
- 修订理由：团队成员主要使用 Java，引入 Python 增加不必要的环境配置和学习成本；Java 的统一工具链更适合 Maven 构建和 CI 集成
