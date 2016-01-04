#include "minui/ir_keycode.h"

#define IR_KEYCODE_HOME 71
#define IR_KEYCODE_UP 67
#define IR_KEYCODE_DOWN 10
#define IR_KEYCODE_ENTER 2

int ir_convert_keycode(int ir_keycode){
    switch (ir_keycode){
        case IR_KEYCODE_HOME:
            return IR_KEY_HOME;
        case IR_KEYCODE_UP:
            return IR_KEY_UP;
        case IR_KEYCODE_DOWN:
            return IR_KEY_DOWN;
        case IR_KEYCODE_ENTER:
            return IR_KEY_ENTER;
    }
    return -1;
}
