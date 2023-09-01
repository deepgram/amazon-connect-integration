import unittest
import lambda_function


class TestGetDgParams(unittest.TestCase):
    def test_get_dg_params(self):
        contact_attrs = {
            "dg_model": "nova",
            "dg_callback": "https://example.com/webhook/{contact-id}",
            "dg_tag": r"someTag1 some\ multi\ word\ tag some\\\ Tag2 some\Tag3 some\\\Tag4 some\\\\Tag5",
            "something": "else",
        }
        expected_dg_params = {
            "model": "nova",
            "callback": "https://example.com/webhook/{contact-id}",
            "tag": [
                r"someTag1",
                r"some multi word tag",
                r"some\ Tag2",
                r"some\Tag3",
                r"some\\Tag4",
                r"some\\Tag5",
            ],
        }
        actual_dg_params = lambda_function.get_dg_params(contact_attrs)

        self.assertEqual(actual_dg_params, expected_dg_params)
