/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// A production build ships each content-hashed module with a Brotli copy beside it, <name>.br, for a host that
// serves precompressed files (node-headless, nginx brotli_static). Only hashed names: a rebuild renames them, so
// a host can never pair a stale .br with new content. Quality 11 takes the app's wasm from 22 MB to 3.7 MB.
if (config.mode === "production") {
    const zlib = require("zlib");
    const HASHED = /(^|\/)[0-9a-f]{20}\.(wasm|js|mjs)$/;
    config.plugins = config.plugins || [];
    config.plugins.push({
        apply(compiler) {
            const { Compilation, sources } = compiler.webpack;
            compiler.hooks.thisCompilation.tap("BrotliHashed", (compilation) => {
                compilation.hooks.processAssets.tap(
                    { name: "BrotliHashed", stage: Compilation.PROCESS_ASSETS_STAGE_OPTIMIZE_TRANSFER },
                    (assets) => {
                        for (const name of Object.keys(assets)) {
                            if (!HASHED.test(name)) continue;
                            const input = compilation.getAsset(name).source.buffer();
                            const output = zlib.brotliCompressSync(input, {
                                params: {
                                    [zlib.constants.BROTLI_PARAM_QUALITY]: 11,
                                    [zlib.constants.BROTLI_PARAM_SIZE_HINT]: input.length,
                                    [zlib.constants.BROTLI_PARAM_MODE]: name.endsWith(".wasm")
                                        ? zlib.constants.BROTLI_MODE_GENERIC
                                        : zlib.constants.BROTLI_MODE_TEXT,
                                },
                            });
                            compilation.emitAsset(`${name}.br`, new sources.RawSource(output));
                        }
                    },
                );
            });
        },
    });
}
