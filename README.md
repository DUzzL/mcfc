# Function Compiler

Check it out on Modrinth [here](https://modrinth.com/mod/mcfc)!

This mod allows the server to compile functions from datapacks on load to Java bytecode, which results in increased performance, because compiled code can run everything at once instead of running commands one by one.

The mod does not collect or upload any data. Compilation statistics and errors are only written to the server log.

This mod will not respect the `maxCommandChainLength` gamerule to improve performance, otherwise it would require the compiled code to check the amount of commands ran, which could be slow.

## Usage

Put the mod on a fabric server with all the dependencies, the mod will automatically try to optimize all functions and commands which are supported.

**If you encounter any crashes, please report them with the server log and, if possible, the datapack that triggers them on the [issue tracker](https://github.com/DUzzL/mcfc/issues).**

## Supported Commands

Of course this mod can't support every command, so it will only generate code for functions which contain only commands which are supported. **Currently, this will not compile every function with macros.** It can't compile some macros because any value could be interpreted in any way, which is hard to implement using only things known at compile-time, but some things could be guessed.

The list of commands supported currently as of `0.3.2-alpha.2` can be found in the [Command Support](/docs/command_support.md) file.

## Supported Selectors

All the selectors are supported, and all their options.

## How it works

This mod works by using Java's class loader: It generates bytecode for the functions at runtime and loads them. If you'd like you can always look at the source in this repository!