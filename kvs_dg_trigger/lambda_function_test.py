import unittest
import lambda_function


class TestGetDgParams(unittest.TestCase):
    def test_general_case(self):
        contact_attrs = {
            "dg_model": "nova",
            "dg_callback": "https://example.com/webhook/{contact-id}/stuff",
            "dg_tag": r"someTag1 some\ multi\ word\ tag some\\\ Tag2 some\Tag3 some\\\Tag4 some\\\\Tag5",
            "something": "else",
        }
        contact_id = "11112222-3333-4444-5555-aaaabbbbcccc"
        expected_dg_params = {
            "model": "nova",
            "callback": "https://example.com/webhook/11112222-3333-4444-5555-aaaabbbbcccc/stuff",
            "tag": [
                r"someTag1",
                r"some multi word tag",
                r"some\ Tag2",
                r"some\Tag3",
                r"some\\Tag4",
                r"some\\Tag5",
                r"dg_amazonconnect",
            ],
        }
        actual_dg_params = lambda_function.get_dg_params(contact_attrs, contact_id)

        self.assertEqual(actual_dg_params, expected_dg_params)

    def test_no_tags(self):
        contact_attrs = {
            "dg_model": "nova",
        }
        contact_id = "11112222-3333-4444-5555-aaaabbbbcccc"
        expected_dg_params = {"model": "nova", "tag": "dg_amazonconnect"}
        actual_dg_params = lambda_function.get_dg_params(contact_attrs, contact_id)

        self.assertEqual(actual_dg_params, expected_dg_params)

    def test_single_tag(self):
        contact_attrs = {"dg_model": "nova", "dg_tag": "someTag"}
        contact_id = "11112222-3333-4444-5555-aaaabbbbcccc"
        expected_dg_params = {"model": "nova", "tag": ["someTag", "dg_amazonconnect"]}
        actual_dg_params = lambda_function.get_dg_params(contact_attrs, contact_id)

        self.assertEqual(actual_dg_params, expected_dg_params)

    def test_contact_attrs_is_none(self):
        contact_attrs = None
        contact_id = "11112222-3333-4444-5555-aaaabbbbcccc"
        expected_dg_params = {"tag": "dg_amazonconnect"}
        actual_dg_params = lambda_function.get_dg_params(contact_attrs, contact_id)

        self.assertEqual(actual_dg_params, expected_dg_params)

    def test_contact_attrs_is_empty(self):
        contact_attrs = dict()
        contact_id = "11112222-3333-4444-5555-aaaabbbbcccc"
        expected_dg_params = {"tag": "dg_amazonconnect"}
        actual_dg_params = lambda_function.get_dg_params(contact_attrs, contact_id)

        self.assertEqual(actual_dg_params, expected_dg_params)

    def test_multiple_callbacks_provided(self):
        contact_attrs = {
            "dg_model": "nova",
            "dg_callback": "https://exampleone.com https://exampletwo.com",
        }
        contact_id = "11112222-3333-4444-5555-aaaabbbbcccc"
        actual_dg_params = lambda_function.get_dg_params(contact_attrs, contact_id)

        self.assertIsNone(actual_dg_params)
