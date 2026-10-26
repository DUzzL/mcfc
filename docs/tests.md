# Tests

`./gradlew build` runs the game tests (`:mc-26-1-fabric:runGameTest`). Every `tests/**/*.mctest` file becomes one game test. The test loads its functions, runs `mcfc-test:run` once compiled and once with vanilla, fails when the scoreboards differ, and then checks the `@validate` lines.

## `.mctest` format

```
; comment
@setup
objective create <name> <criterion>

@function <name>            ; mcfc-test:<name>, or <namespace>:<path>
<commands>

@file <namespace>:<path>    ; any other datapack file, e.g. a function tag
<content>

@validate
score <holder> <objective> is <value>
no_fallback                 ; the compiler reported no diagnostic: every command compiled
```

Tests that need generated content or Java-side checks live in `mc-26-1-fabric/src/gametest` (for example `ManyConstantsTest`).
