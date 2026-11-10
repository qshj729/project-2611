# CI 工作流放在哪里？

GitHub Actions **只扫描仓库根目录的 `.github/workflows/`**。本仓库的 Gradle 工程在 `android/` 下，
所以真正生效的工作流在：

> [`../../.github/workflows/ci.yml`](../../.github/workflows/ci.yml)

`android/.github/workflows/ci.yml` 曾经放在这里，GitHub 永远不会执行它（标题写的「让 CI 真正可跑」
指的就是这个问题）。该文件已移到仓库根目录，本目录只保留这个说明。

改动 CI 时请记住：

* 所有 `./gradlew` 命令都要在 `android/` 下执行（workflow 用 job 级 `defaults.run.working-directory: android`）。
* JDK 固定 17（与 `scripts/hubdeck-env.sh` 里的本地工具链一致）。
* 依赖缓存用 `gradle/actions/setup-gradle@v3`（不要再叠加 `setup-java` 的 `cache: gradle`，两者会打架）。
* 产物上传路径相对**仓库根目录**，所以是 `android/app/build/outputs/apk/debug/*.apk`。
* 有两条 Wrapper 校验：`gradle/wrapper-validation-action` 校验官方 checksum，
  `./gradlew wrapper` + `git diff --exit-code` 校验仓库里的 wrapper 与声明版本一致。
