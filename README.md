# Function Compiler

Check it out on Modrinth [here](https://modrinth.com/mod/mcfc)!

This mod allows the server to compile functions from datapacks on load to Java bytecode, which results in increased performance, because compiled code can run everything at once instead of running commands one by one.

This mod will not respect the `maxCommandChainLength` gamerule to improve performance, otherwise it would require the compiled code to check the amount of commands ran, which could be slow.

## Usage

Put the mod on a fabric server with all of the dependencies, the mod will automatically try to optimize all functions and commands which are supported.

**If you encounter any crashes, please report them on the [issue tracker](https://gitlab.epicpix.dev/EpicPix/MinecraftFunctionCompiler/-/issues) and turn off the mod temporarily until the issue won't be fixed**

## Supported Commands

Of course this mod can't support every command, so it will only generate code for functions which contain only commands which are supported, **this will not compile functions with macros currently**. It can't compile macros because any value could be interpreted in any way, which is hard to implement using only things known at compile-time.

This is the list of commands supported currently as of `0.1.0-alpha.1`:
- `execute as`
- `execute if entity`
- `execute if score`
- `execute run`
- `function` (without ability to provide arguments)
- `kill`
- `scoreboard players add`
- `scoreboard players operation` - `=`, `+=`, `-=`, `*=` (other operators coming soon)
- `scoreboard players remove`
- `scoreboard players reset` (only with the objective specified)
- `scoreboard players set`
- `tag add`
- `tag remove`

## Supported Selectors

Not all selectors are supported as well, currently the only supported selectors are by UUID, player name, `@e`, `@a`, `@s`, with allowed selector parameters being `limit` and `tag`.

## How it works

This mod works by using Java's class loader and generating bytecode for the functions at runtime and loading them, if you'd like you can always look at the source in this repository!