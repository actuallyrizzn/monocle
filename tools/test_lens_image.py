#!/usr/bin/env python3
import unittest

from lens_image import mosaic_code, palette_and_indices, rle_rects, smiley_grid


class MosaicTests(unittest.TestCase):
    def test_rle_merges_horizontal(self):
        grid = [[1, 1, 1, 2], [3, 3, 3, 3]]
        rects = rle_rects(grid, 10, 10)
        self.assertEqual(len(rects), 3)
        self.assertEqual(rects[0][4], 1)
        self.assertEqual(rects[0][2], 29)

    def test_palette_cap(self):
        pal, indexed = palette_and_indices([(0, 0, 1, 1, 0xFF0000), (2, 0, 3, 1, 0xFF0000)])
        self.assertEqual(len(pal), 1)
        self.assertEqual(indexed[0][4], 0)
        self.assertEqual(indexed[1][4], 0)

    def test_smiley_code_is_decoder_not_one_line_text(self):
        code = mosaic_code(smiley_grid(8), 80, 50)
        self.assertIn("display.Rectangle", code)
        self.assertIn("display.show(o)", code)
        self.assertNotIn("display.Text", code)
        self.assertLess(len(code), 8000)


if __name__ == "__main__":
    unittest.main()
