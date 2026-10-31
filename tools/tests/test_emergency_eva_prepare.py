"""Guard the real default Overworld without rejecting unrelated flat mod dimensions."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("eva_prepare", Path(__file__).parents[1] / "prepare_emergency_eva_playtest.py")
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


def string(value):
    raw = value.encode()
    return len(raw).to_bytes(2, "big") + raw


def dimension(generator, settings):
    return prepare.named(10, "generator", prepare.named(8, "type", string(generator))
                         + prepare.named(8, "settings", string(settings)) + b"\x00") + b"\x00"


def metadata(overworld_type="minecraft:noise", settings="minecraft:overworld"):
    dimensions = prepare.named(10, "minecraft:overworld", dimension(overworld_type, settings))
    dimensions += prepare.named(10, "frozendawn:thae_iven", dimension("minecraft:flat", "minecraft:the_void"))
    dimensions += prepare.named(10, "minecraft:the_nether", dimension("minecraft:noise", "minecraft:nether"))
    worldgen = prepare.named(10, "dimensions", dimensions + b"\x00") + b"\x00"
    data = prepare.named(10, "WorldGenSettings", worldgen) + b"\x00"
    return b"\x0a\x00\x00" + prepare.named(10, "Data", data) + b"\x00"


class DefaultTerrainGuardTest(unittest.TestCase):
    def test_accepts_default_overworld_with_flat_mod_dimension(self):
        result = prepare.fresh_metadata(metadata(), prepare.CONTINUITY_TITLE)
        self.assertIn(prepare.CONTINUITY_TITLE.encode(), result)
        self.assertIn(b"frozendawn:thae_iven", result)

    def test_rejects_flat_overworld_even_when_nether_is_noise(self):
        with self.assertRaisesRegex(SystemExit, "default terrain Overworld"):
            prepare.fresh_metadata(metadata("minecraft:flat"))

    def test_rejects_amplified_overworld(self):
        with self.assertRaisesRegex(SystemExit, "default terrain Overworld"):
            prepare.fresh_metadata(metadata(settings="minecraft:amplified"))


if __name__ == "__main__":
    unittest.main()
