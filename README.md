# Function Compiler

Check it out on Modrinth [here](https://modrinth.com/mod/mcfc)!

This mod allows the server to compile functions from datapacks on load to Java bytecode, which results in increased performance, because compiled code can run everything at once instead of running commands one by one.

Some statistics are automatically collected when using this mod. Feel free to check the [code](/common/src/main/java/dev/epicpix/minecraftfunctioncompiler/reporter/FunctionCompilerReporter.java) to check what is reported, but the main things are: timings, function counts, instruction counts, exceptions/crashes (those might contain the commands for debugging).

This mod will not respect the `maxCommandChainLength` gamerule to improve performance, otherwise it would require the compiled code to check the amount of commands ran, which could be slow.

## Usage

Put the mod on a fabric server with all the dependencies, the mod will automatically try to optimize all functions and commands which are supported.

**If you encounter any crashes, they will be automatically reported. If you have more context to how to trigger a crash, please report it on the [issue tracker](https://gitlab.epicpix.dev/EpicPix/MinecraftFunctionCompiler/-/issues).**

## Supported Commands

Of course this mod can't support every command, so it will only generate code for functions which contain only commands which are supported. **Currently, this will not compile functions with macros.** It can't compile macros because any value could be interpreted in any way, which is hard to implement using only things known at compile-time.

The list of commands supported currently as of `0.3.0-alpha.1` can be found in the [Command Support](/docs/command_support.md) file.

## Supported Selectors

Currently, the majority of selector parameters are implemented, the only ones not implemented are `advancements`, `dx`, `dy`, `dz`, `nbt`, `predicate`, `x`, `y`, `z`.

## How it works

This mod works by using Java's class loader: It generates bytecode for the functions at runtime and loads them. If you'd like you can always look at the source in this repository!