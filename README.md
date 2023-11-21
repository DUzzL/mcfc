# Function Compiler

Check it out on Modrinth [here](https://modrinth.com/mod/mcfc)!

This mod allows the server to compile functions from datapacks on load to Java bytecode, which results in increased performance, because compiled code can run everything at once instead of running commands one by one.

Some statistics are automatically collected when using this mod feel free to check the [code](/common/src/main/kotlin/dev/epicpix/minecraftfunctioncompiler/reporter/FunctionCompilerReporter.kt) to check what is reported, the main things that are reported are: timings, how many functions got found/parsed/compiled, exceptions/crashes.

This mod will not respect the `maxCommandChainLength` gamerule to improve performance, otherwise it would require the compiled code to check the amount of commands ran, which could be slow.

## Usage

Put the mod on a fabric server with all the dependencies, the mod will automatically try to optimize all functions and commands which are supported.

**If you encounter any crashes, they will be automatically reported. If you have more context to how to trigger a crash, please report it on the [issue tracker](https://gitlab.epicpix.dev/EpicPix/MinecraftFunctionCompiler/-/issues).**

## Supported Commands

Of course this mod can't support every command, so it will only generate code for functions which contain only commands which are supported, **this will not compile functions with macros currently**. It can't compile macros because any value could be interpreted in any way, which is hard to implement using only things known at compile-time.

The list of commands supported currently as of `0.2.0-alpha.3` is in the [Command Support](/docs/command_support.md) file

## Supported Selectors

Not all selectors are supported as well, currently the only supported selectors are by UUID, player name, `@e`, `@a`, `@s`, with allowed selector parameters being `gamemode`, `limit`, `tag`, `team`, `type` and `scores`.

## How it works

This mod works by using Java's class loader and generating bytecode for the functions at runtime and loading them, if you'd like you can always look at the source in this repository!