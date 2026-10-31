package ua.stetsenkoinna.graphpresentation.ai;

/** Shared discovery and validation contracts for direct model operations. */
final class ModelSchemas {
    static final String DOCUMENT = """
{
  "type": "object",
  "properties": {
    "name": {
      "type": "string",
      "minLength": 1
    },
    "places": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "id": {
            "type": "string"
          },
          "name": {
            "type": "string"
          },
          "tokens": {
            "type": "integer",
            "minimum": 0
          },
          "tokens_parameter": {
            "type": "string"
          },
          "x": {
            "type": "number",
            "minimum": -1000000000,
            "maximum": 1000000000
          },
          "y": {
            "type": "number",
            "minimum": -1000000000,
            "maximum": 1000000000
          }
        },
        "required": [
          "id",
          "tokens",
          "x",
          "y"
        ],
        "additionalProperties": false
      },
      "maxItems": 2000
    },
    "transitions": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "id": {
            "type": "string"
          },
          "name": {
            "type": "string"
          },
          "delay": {
            "type": "number",
            "minimum": 0
          },
          "distribution": {
            "type": "string",
            "enum": [
              "deterministic",
              "exp",
              "unif",
              "norm"
            ]
          },
          "deviation": {
            "type": "number",
            "minimum": 0
          },
          "priority": {
            "type": "integer",
            "minimum": 0
          },
          "probability": {
            "type": "number",
            "minimum": 0
          },
          "delay_parameter": {
            "type": "string"
          },
          "distribution_parameter": {
            "type": "string"
          },
          "priority_parameter": {
            "type": "string"
          },
          "probability_parameter": {
            "type": "string"
          },
          "x": {
            "type": "number",
            "minimum": -1000000000,
            "maximum": 1000000000
          },
          "y": {
            "type": "number",
            "minimum": -1000000000,
            "maximum": 1000000000
          }
        },
        "required": [
          "id",
          "delay",
          "x",
          "y"
        ],
        "additionalProperties": false
      },
      "maxItems": 2000
    },
    "arcs": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "id": {
            "type": "string"
          },
          "source": {
            "type": "string"
          },
          "target": {
            "type": "string"
          },
          "weight": {
            "type": "integer",
            "minimum": 1
          },
          "read": {
            "type": "boolean"
          },
          "weight_parameter": {
            "type": "string"
          },
          "read_parameter": {
            "type": "string"
          },
          "boundary_stub": {
            "type": "object",
            "properties": {
              "x": {
                "type": "number",
                "minimum": -1000000000,
                "maximum": 1000000000
              },
              "y": {
                "type": "number",
                "minimum": -1000000000,
                "maximum": 1000000000
              }
            },
            "required": [
              "x",
              "y"
            ],
            "additionalProperties": false
          }
        },
        "required": [
          "id",
          "source",
          "target"
        ],
        "additionalProperties": false
      },
      "maxItems": 8000
    },
    "objects": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "id": {
            "type": "string"
          },
          "name": {
            "type": "string"
          },
          "x": {
            "type": "number",
            "minimum": -1000000000,
            "maximum": 1000000000
          },
          "y": {
            "type": "number",
            "minimum": -1000000000,
            "maximum": 1000000000
          },
          "width": {
            "type": "integer",
            "minimum": 120,
            "maximum": 1000000
          },
          "height": {
            "type": "integer",
            "minimum": 80,
            "maximum": 1000000
          },
          "priority": {
            "type": "integer",
            "minimum": 0
          },
          "members": {
            "type": "array",
            "items": {
              "type": "string"
            },
            "maxItems": 8000
          },
          "parent": {
            "type": "string"
          },
          "collapsed": {
            "type": "boolean"
          },
          "content_visible": {
            "type": "boolean"
          },
          "template": {
            "type": "object",
            "properties": {
              "method": {
                "type": "string"
              },
              "arguments": {
                "type": "array",
                "items": {
                  "type": "string"
                },
                "maxItems": 100
              }
            },
            "required": [
              "method"
            ],
            "additionalProperties": false
          }
        },
        "required": [
          "id",
          "name",
          "x",
          "y",
          "width",
          "height",
          "members"
        ],
        "additionalProperties": false
      },
      "maxItems": 1000
    },
    "links": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "id": {
            "type": "string"
          },
          "type": {
            "type": "string",
            "enum": [
              "placeFusion",
              "transitionToPlace"
            ]
          },
          "source": {
            "type": "string"
          },
          "target": {
            "type": "string"
          },
          "weight": {
            "type": "integer",
            "minimum": 1
          },
          "weight_parameter": {
            "type": "string"
          },
          "boundary_stub": {
            "type": "object",
            "properties": {
              "x": {
                "type": "number",
                "minimum": -1000000000,
                "maximum": 1000000000
              },
              "y": {
                "type": "number",
                "minimum": -1000000000,
                "maximum": 1000000000
              }
            },
            "required": [
              "x",
              "y"
            ],
            "additionalProperties": false
          }
        },
        "required": [
          "type",
          "source",
          "target"
        ],
        "additionalProperties": false
      },
      "maxItems": 8000
    },
    "groups": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "id": {
            "type": "string"
          },
          "name": {
            "type": "string"
          },
          "objects": {
            "type": "array",
            "items": {
              "type": "string"
            },
            "maxItems": 1000
          },
          "template": {
            "type": "object",
            "properties": {
              "method": {
                "type": "string"
              },
              "arguments": {
                "type": "array",
                "items": {
                  "type": "string"
                },
                "maxItems": 100
              }
            },
            "required": [
              "method"
            ],
            "additionalProperties": false
          }
        },
        "required": [
          "id",
          "name",
          "objects"
        ],
        "additionalProperties": false
      },
      "maxItems": 1000
    }
  },
  "required": [
    "name",
    "places",
    "transitions",
    "arcs"
  ],
  "additionalProperties": false
}
        """;
    static final String EDIT = """
{
  "type": "object",
  "properties": {
    "expected_revision": {
      "type": "string"
    },
    "operations": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "id": {
            "type": "string"
          },
          "name": {
            "type": "string"
          },
          "tokens": {
            "type": "integer",
            "minimum": 0
          },
          "tokens_parameter": {
            "type": "string"
          },
          "x": {
            "type": "number",
            "minimum": -1000000000,
            "maximum": 1000000000
          },
          "y": {
            "type": "number",
            "minimum": -1000000000,
            "maximum": 1000000000
          },
          "delay": {
            "type": "number",
            "minimum": 0
          },
          "distribution": {
            "type": "string",
            "enum": [
              "deterministic",
              "exp",
              "unif",
              "norm"
            ]
          },
          "deviation": {
            "type": "number",
            "minimum": 0
          },
          "priority": {
            "type": "integer",
            "minimum": 0
          },
          "probability": {
            "type": "number",
            "minimum": 0
          },
          "delay_parameter": {
            "type": "string"
          },
          "distribution_parameter": {
            "type": "string"
          },
          "priority_parameter": {
            "type": "string"
          },
          "probability_parameter": {
            "type": "string"
          },
          "source": {
            "type": "string"
          },
          "target": {
            "type": "string"
          },
          "weight": {
            "type": "integer",
            "minimum": 1
          },
          "read": {
            "type": "boolean"
          },
          "weight_parameter": {
            "type": "string"
          },
          "read_parameter": {
            "type": "string"
          },
          "boundary_stub": {
            "type": "object",
            "properties": {
              "x": {
                "type": "number",
                "minimum": -1000000000,
                "maximum": 1000000000
              },
              "y": {
                "type": "number",
                "minimum": -1000000000,
                "maximum": 1000000000
              }
            },
            "required": [
              "x",
              "y"
            ],
            "additionalProperties": false
          },
          "width": {
            "type": "integer",
            "minimum": 120,
            "maximum": 1000000
          },
          "height": {
            "type": "integer",
            "minimum": 80,
            "maximum": 1000000
          },
          "members": {
            "type": "array",
            "items": {
              "type": "string"
            },
            "maxItems": 8000
          },
          "parent": {
            "type": "string"
          },
          "collapsed": {
            "type": "boolean"
          },
          "content_visible": {
            "type": "boolean"
          },
          "template": {
            "type": "object",
            "properties": {
              "method": {
                "type": "string"
              },
              "arguments": {
                "type": "array",
                "items": {
                  "type": "string"
                },
                "maxItems": 100
              }
            },
            "required": [
              "method"
            ],
            "additionalProperties": false
          },
          "type": {
            "type": "string",
            "enum": [
              "placeFusion",
              "transitionToPlace"
            ]
          },
          "objects": {
            "type": "array",
            "items": {
              "type": "string"
            },
            "maxItems": 1000
          },
          "op": {
            "type": "string",
            "enum": [
              "put_place",
              "put_transition",
              "put_arc",
              "put_object",
              "put_group",
              "connect_ports",
              "disconnect_ports",
              "remove",
              "replicate_object",
              "move_object",
              "rename_model",
              "set_owner"
            ]
          },
          "dx": {
            "type": "number",
            "minimum": -1000000000,
            "maximum": 1000000000
          },
          "dy": {
            "type": "number",
            "minimum": -1000000000,
            "maximum": 1000000000
          },
          "count": {
            "type": "integer",
            "minimum": 1,
            "maximum": 200
          },
          "prefix": {
            "type": "string"
          },
          "cascade": {
            "type": "boolean"
          },
          "include_external_links": {
            "type": "boolean"
          },
          "group_id": {
            "type": "string"
          },
          "include_original": {
            "type": "boolean"
          }
        },
        "required": [
          "op"
        ],
        "additionalProperties": false
      },
      "maxItems": 8000,
      "minItems": 1
    }
  },
  "required": [
    "expected_revision",
    "operations"
  ],
  "additionalProperties": false
}
        """;
    static final String APPLY = """
{
  "type": "object",
  "properties": {
    "expected_revision": {
      "type": "string"
    },
    "replace_current": {
      "type": "boolean"
    },
    "model": {
      "type": "object",
      "properties": {
        "name": {
          "type": "string",
          "minLength": 1
        },
        "places": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": {
                "type": "string"
              },
              "name": {
                "type": "string"
              },
              "tokens": {
                "type": "integer",
                "minimum": 0
              },
              "tokens_parameter": {
                "type": "string"
              },
              "x": {
                "type": "number",
                "minimum": -1000000000,
                "maximum": 1000000000
              },
              "y": {
                "type": "number",
                "minimum": -1000000000,
                "maximum": 1000000000
              }
            },
            "required": [
              "id",
              "tokens",
              "x",
              "y"
            ],
            "additionalProperties": false
          },
          "maxItems": 2000
        },
        "transitions": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": {
                "type": "string"
              },
              "name": {
                "type": "string"
              },
              "delay": {
                "type": "number",
                "minimum": 0
              },
              "distribution": {
                "type": "string",
                "enum": [
                  "deterministic",
                  "exp",
                  "unif",
                  "norm"
                ]
              },
              "deviation": {
                "type": "number",
                "minimum": 0
              },
              "priority": {
                "type": "integer",
                "minimum": 0
              },
              "probability": {
                "type": "number",
                "minimum": 0
              },
              "delay_parameter": {
                "type": "string"
              },
              "distribution_parameter": {
                "type": "string"
              },
              "priority_parameter": {
                "type": "string"
              },
              "probability_parameter": {
                "type": "string"
              },
              "x": {
                "type": "number",
                "minimum": -1000000000,
                "maximum": 1000000000
              },
              "y": {
                "type": "number",
                "minimum": -1000000000,
                "maximum": 1000000000
              }
            },
            "required": [
              "id",
              "delay",
              "x",
              "y"
            ],
            "additionalProperties": false
          },
          "maxItems": 2000
        },
        "arcs": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": {
                "type": "string"
              },
              "source": {
                "type": "string"
              },
              "target": {
                "type": "string"
              },
              "weight": {
                "type": "integer",
                "minimum": 1
              },
              "read": {
                "type": "boolean"
              },
              "weight_parameter": {
                "type": "string"
              },
              "read_parameter": {
                "type": "string"
              },
              "boundary_stub": {
                "type": "object",
                "properties": {
                  "x": {
                    "type": "number",
                    "minimum": -1000000000,
                    "maximum": 1000000000
                  },
                  "y": {
                    "type": "number",
                    "minimum": -1000000000,
                    "maximum": 1000000000
                  }
                },
                "required": [
                  "x",
                  "y"
                ],
                "additionalProperties": false
              }
            },
            "required": [
              "id",
              "source",
              "target"
            ],
            "additionalProperties": false
          },
          "maxItems": 8000
        },
        "objects": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": {
                "type": "string"
              },
              "name": {
                "type": "string"
              },
              "x": {
                "type": "number",
                "minimum": -1000000000,
                "maximum": 1000000000
              },
              "y": {
                "type": "number",
                "minimum": -1000000000,
                "maximum": 1000000000
              },
              "width": {
                "type": "integer",
                "minimum": 120,
                "maximum": 1000000
              },
              "height": {
                "type": "integer",
                "minimum": 80,
                "maximum": 1000000
              },
              "priority": {
                "type": "integer",
                "minimum": 0
              },
              "members": {
                "type": "array",
                "items": {
                  "type": "string"
                },
                "maxItems": 8000
              },
              "parent": {
                "type": "string"
              },
              "collapsed": {
                "type": "boolean"
              },
              "content_visible": {
                "type": "boolean"
              },
              "template": {
                "type": "object",
                "properties": {
                  "method": {
                    "type": "string"
                  },
                  "arguments": {
                    "type": "array",
                    "items": {
                      "type": "string"
                    },
                    "maxItems": 100
                  }
                },
                "required": [
                  "method"
                ],
                "additionalProperties": false
              }
            },
            "required": [
              "id",
              "name",
              "x",
              "y",
              "width",
              "height",
              "members"
            ],
            "additionalProperties": false
          },
          "maxItems": 1000
        },
        "links": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": {
                "type": "string"
              },
              "type": {
                "type": "string",
                "enum": [
                  "placeFusion",
                  "transitionToPlace"
                ]
              },
              "source": {
                "type": "string"
              },
              "target": {
                "type": "string"
              },
              "weight": {
                "type": "integer",
                "minimum": 1
              },
              "weight_parameter": {
                "type": "string"
              },
              "boundary_stub": {
                "type": "object",
                "properties": {
                  "x": {
                    "type": "number",
                    "minimum": -1000000000,
                    "maximum": 1000000000
                  },
                  "y": {
                    "type": "number",
                    "minimum": -1000000000,
                    "maximum": 1000000000
                  }
                },
                "required": [
                  "x",
                  "y"
                ],
                "additionalProperties": false
              }
            },
            "required": [
              "type",
              "source",
              "target"
            ],
            "additionalProperties": false
          },
          "maxItems": 8000
        },
        "groups": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": {
                "type": "string"
              },
              "name": {
                "type": "string"
              },
              "objects": {
                "type": "array",
                "items": {
                  "type": "string"
                },
                "maxItems": 1000
              },
              "template": {
                "type": "object",
                "properties": {
                  "method": {
                    "type": "string"
                  },
                  "arguments": {
                    "type": "array",
                    "items": {
                      "type": "string"
                    },
                    "maxItems": 100
                  }
                },
                "required": [
                  "method"
                ],
                "additionalProperties": false
              }
            },
            "required": [
              "id",
              "name",
              "objects"
            ],
            "additionalProperties": false
          },
          "maxItems": 1000
        }
      },
      "required": [
        "name",
        "places",
        "transitions",
        "arcs"
      ],
      "additionalProperties": false
    }
  },
  "required": [
    "expected_revision",
    "model"
  ],
  "additionalProperties": false
}
        """;
}
