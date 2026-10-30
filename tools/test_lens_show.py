#!/usr/bin/env python3
import unittest

from lens_layout import (
    BAT_COLS,
    BODY_Y,
    COLS,
    FONT_HEIGHT,
    LINE_H,
    SUBJ_COLS,
    WIDTH,
    card_code,
    clip,
    wrap,
)


class WrapTests(unittest.TestCase):
    def test_cols_match_firmware(self):
        self.assertEqual(WIDTH // 24, COLS)
        self.assertEqual(26, COLS)
        self.assertGreater(LINE_H, FONT_HEIGHT)
        self.assertEqual(21, SUBJ_COLS)
        self.assertEqual(100, BODY_Y)

    def test_words_fit(self):
        lines = wrap("Otto here. The last note ran off the side of the glass.")
        self.assertTrue(all(len(line) <= COLS for line in lines))
        self.assertGreater(len(lines), 1)

    def test_long_word_splits(self):
        lines = wrap("abcdefghijklmnopqrstuvwxyz0123", cols=10)
        self.assertEqual(lines[0], "abcdefghij")
        self.assertTrue(all(len(line) <= 10 for line in lines))

    def test_subject_does_not_overlap_battery(self):
        subj = clip("abcdefghijklmnopqrstuvwxyz", SUBJ_COLS)
        self.assertEqual(len(subj), SUBJ_COLS)
        self.assertLess(len(subj) * 24, 640 - BAT_COLS * 24)

    def test_card_has_chrome_not_rail(self):
        code = card_code(
            "Still building the Moya flag.",
            sender="Otto",
            subject="Sanctum",
        )
        self.assertIn("S='Sanctum'", code)
        self.assertIn("W='Otto'", code)
        self.assertIn("def chrome():", code)
        self.assertIn("battery_level", code)
        self.assertIn("bx=640-len(b)*24", code)
        self.assertIn("display.Text(S,0,0,", code)
        self.assertIn("display.Text(W,0,50,", code)
        self.assertNotIn("display.Fill(", code)
        self.assertNotIn("display.Rectangle(", code)
        self.assertTrue(all(len(line) <= COLS for line in wrap("Still building the Moya flag.")))


if __name__ == "__main__":
    unittest.main()
