/*
   Copyright 2018 Bart van Helvert

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package io.github.runedata.cache.content

/** Represents an .idx file in the cache with the [id] representing the suffix in the file name. */
enum class IndexType(val id: Int) {
    SKELETONS(0),
    SKINS(1),
    CONFIGS(2),
    INTERFACES(3),
    SOUNDEFFECTS(4),
    REGIONS(5),
    TRACK1(6),
    MODELS(7),
    SPRITES(8),
    TEXTURES(9),
    BINARY(10),
    TRACK2(11),
    CLIENTSCRIPT(12),
    FONTS(13),
    VORBIS(14),
    INSTRUMENTS(15),
    UNDERMINED(16),
    DEFAULTS(17),
    REFERENCE(255),
}
