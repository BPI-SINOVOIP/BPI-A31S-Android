/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include <cutils/android_reboot.h>

#include "common.h"
#include "device.h"
#include "minui/minui.h"
#include "minui/script.h"
#include "screen_ui.h"
#include "ui.h"
#include "minui/ir_keycode.h"

#define UI_WAIT_KEY_TIMEOUT_SEC    120

static char GET_ITEM_MAINKEY[]	="card_boot"; /* "card0_boot_para" */
static char LED_SCRIPT_GPIO[]	="sprite_gpio0"; /* led gpio */
static char LED_SCRIPT_NORMAL[]	="sprite_work_delay"; /* led gpio */
static char LED_SCRIPT_ERROR[]	="sprite_err_delay"; /* led gpio */

// There's only (at most) one of these objects, and global callbacks
// (for pthread_create, and the input event system) need to find it,
// so use a global variable.
static RecoveryUI* self = NULL;

int recovery_rotation;

RecoveryUI::RecoveryUI() :
    touchDistanceThreshold(30),
    key_queue_len(0),
    key_last_down(-1),
    key_long_press(false),
    key_down_count(0),
    touchStage(0),
    LED_NORMAL(500),
    LED_ERROR(50),
    LED_FLICKER(500),
    eventStage(0) {
    pthread_mutex_init(&key_queue_mutex, NULL);
    pthread_cond_init(&key_queue_cond, NULL);
    self = this;
}

void RecoveryUI::Init() {
	char path[255];
    ev_init(input_callback, NULL);
    pthread_create(&input_t, NULL, input_thread, NULL);

    get_item_out_t *pitem;
    pitem =get_sub_item(GET_ITEM_MAINKEY,LED_SCRIPT_GPIO);
    dump_subkey(pitem);
    if(pitem!=NULL&&pitem->str!=NULL){
        snprintf(path,sizeof(path),"/sys/class/gpio_sw/%s/data",pitem->str);
        if(!open_gpio(path))
        pthread_create(&input_t, NULL, gpio_thread, NULL);
    }

    pitem = get_sub_item(GET_ITEM_MAINKEY,LED_SCRIPT_NORMAL);
    dump_subkey(pitem);
    if(pitem!=NULL&&pitem->item.val!=0){
        LED_NORMAL = pitem->item.val;
        printf("-----LED_NORMAL=%d------\n",LED_NORMAL);
    }
    pitem = get_sub_item(GET_ITEM_MAINKEY,LED_SCRIPT_ERROR);
    dump_subkey(pitem);
    if(pitem!=NULL&&pitem->item.val!=0){
        LED_ERROR = pitem->item.val;
        printf("-----LED_ERROR=%d------\n",LED_ERROR);
    }
}


int RecoveryUI::input_callback(int fd, short revents, void* data)
{
    struct input_event ev;
    int ret;

    ret = ev_get_input(fd, revents, &ev);
    if (ret)
        return -1;

#ifdef BOARD_IR_RECOVERY
    if(self->ir_handle_input(ev))
        return 0;
#endif

    if (ev.type == EV_SYN) {
        return 0;
    } else if (ev.type == EV_REL) {
        if (ev.code == REL_Y) {
            // accumulate the up or down motion reported by
            // the trackball.  When it exceeds a threshold
            // (positive or negative), fake an up/down
            // key event.
            self->rel_sum += ev.value;
            if (self->rel_sum > 3) {
                self->process_key(KEY_DOWN, 1);   // press down key
                self->process_key(KEY_DOWN, 0);   // and release it
                self->rel_sum = 0;
            } else if (self->rel_sum < -3) {
                self->process_key(KEY_UP, 1);     // press up key
                self->process_key(KEY_UP, 0);     // and release it
                self->rel_sum = 0;
            }
        }
    } else {
        self->rel_sum = 0;
    }

    if (ev.type == EV_KEY && ev.code <= KEY_MAX)
        self->process_key(ev.code, ev.value);

    return 0;
}

// Process a key-up or -down event.  A key is "registered" when it is
// pressed and then released, with no other keypresses or releases in
// between.  Registered keys are passed to CheckKey() to see if it
// should trigger a visibility toggle, an immediate reboot, or be
// queued to be processed next time the foreground thread wants a key
// (eg, for the menu).
//
// We also keep track of which keys are currently down so that
// CheckKey can call IsKeyPressed to see what other keys are held when
// a key is registered.
//
// updown == 1 for key down events; 0 for key up events
void RecoveryUI::process_key(int key_code, int updown) {
    bool register_key = false;
    bool long_press = false;

    pthread_mutex_lock(&key_queue_mutex);
    key_pressed[key_code] = updown;
    if (updown) {
        ++key_down_count;
        key_last_down = key_code;
        key_long_press = false;
        pthread_t th;
        key_timer_t* info = new key_timer_t;
        info->ui = this;
        info->key_code = key_code;
        info->count = key_down_count;
        pthread_create(&th, NULL, &RecoveryUI::time_key_helper, info);
        pthread_detach(th);
    } else {
        if (key_last_down == key_code) {
            long_press = key_long_press;
            register_key = true;
        }
        key_last_down = -1;
    }
    pthread_mutex_unlock(&key_queue_mutex);

    if (register_key) {
        NextCheckKeyIsLong(long_press);
        switch (CheckKey(key_code)) {
          case RecoveryUI::IGNORE:
            break;

          case RecoveryUI::TOGGLE:
            ShowText(!IsTextVisible());
            break;

          case RecoveryUI::REBOOT:
            android_reboot(ANDROID_RB_RESTART, 0, 0);
            break;

          case RecoveryUI::ENQUEUE:
            EnqueueKey(key_code);
            break;
        }
    }
}

void* RecoveryUI::time_key_helper(void* cookie) {
    key_timer_t* info = (key_timer_t*) cookie;
    info->ui->time_key(info->key_code, info->count);
    delete info;
    return NULL;
}

void RecoveryUI::time_key(int key_code, int count) {
    usleep(750000);  // 750 ms == "long"
    bool long_press = false;
    pthread_mutex_lock(&key_queue_mutex);
    if (key_last_down == key_code && key_down_count == count) {
        long_press = key_long_press = true;
    }
    pthread_mutex_unlock(&key_queue_mutex);
    if (long_press) KeyLongPress(key_code);
}

void RecoveryUI::EnqueueKey(int key_code) {
    pthread_mutex_lock(&key_queue_mutex);
    const int queue_max = sizeof(key_queue) / sizeof(key_queue[0]);
    if (key_queue_len < queue_max) {
        key_queue[key_queue_len++] = key_code;
        pthread_cond_signal(&key_queue_cond);
    }
    pthread_mutex_unlock(&key_queue_mutex);
}


// Reads input events, handles special hot keys, and adds to the key queue.
void* RecoveryUI::input_thread(void *cookie)
{
    for (;;) {
        if (!ev_wait(-1))
            ev_dispatch();
    }
    return NULL;
}

void* RecoveryUI::gpio_thread(void *cookie){
	int i=0;
	for(;;){
        i = ~i;
        usleep(self->LED_FLICKER*1000);
        if(i){
            write_gpio("1");
        }else{
            write_gpio("0");
        }
    }
    return NULL;
}
void RecoveryUI::set_led_flicker(int ms){
     LED_FLICKER = ms;
}

int RecoveryUI::WaitKey()
{
    pthread_mutex_lock(&key_queue_mutex);

    // Time out after UI_WAIT_KEY_TIMEOUT_SEC, unless a USB cable is
    // plugged in.
    do {
        struct timeval now;
        struct timespec timeout;
        gettimeofday(&now, NULL);
        timeout.tv_sec = now.tv_sec;
        timeout.tv_nsec = now.tv_usec * 1000;
        timeout.tv_sec += UI_WAIT_KEY_TIMEOUT_SEC;

        int rc = 0;
        while (key_queue_len == 0 && rc != ETIMEDOUT) {
            rc = pthread_cond_timedwait(&key_queue_cond, &key_queue_mutex,
                                        &timeout);
        }
    } while (usb_connected() && key_queue_len == 0);

    int key = -1;
    if (key_queue_len > 0) {
        key = key_queue[0];
        memcpy(&key_queue[0], &key_queue[1], sizeof(int) * --key_queue_len);
    }
    pthread_mutex_unlock(&key_queue_mutex);
    return key;
}

// Return true if USB is connected.
bool RecoveryUI::usb_connected() {
    int fd = open("/sys/class/android_usb/android0/state", O_RDONLY);
    if (fd < 0) {
        printf("failed to open /sys/class/android_usb/android0/state: %s\n",
               strerror(errno));
        return 0;
    }

    char buf;
    /* USB is connected if android_usb state is CONNECTED or CONFIGURED */
    int connected = (read(fd, &buf, 1) == 1) && (buf == 'C');
    if (close(fd) < 0) {
        printf("failed to close /sys/class/android_usb/android0/state: %s\n",
               strerror(errno));
    }
    return connected;
}

bool RecoveryUI::IsKeyPressed(int key)
{
    pthread_mutex_lock(&key_queue_mutex);
    int pressed = key_pressed[key];
    pthread_mutex_unlock(&key_queue_mutex);
    return pressed;
}

void RecoveryUI::FlushKeys() {
    pthread_mutex_lock(&key_queue_mutex);
    key_queue_len = 0;
    pthread_mutex_unlock(&key_queue_mutex);
}

RecoveryUI::KeyAction RecoveryUI::CheckKey(int key) {
    return RecoveryUI::ENQUEUE;
}

void RecoveryUI::NextCheckKeyIsLong(bool is_long_press) {
}

void RecoveryUI::KeyLongPress(int key) {
}

int RecoveryUI::menu_select(-1);

int RecoveryUI::touch_handle_input(input_event ev){
    int touch_code = 0;
    if(ev.type==EV_ABS){
        switch(ev.code){
            case ABS_MT_TRACKING_ID:
                if (touchStage==0)
                {
                    touchStage=1;
                }
                eventStage=1;
                tmpEvent.point_id = ev.value;
                break;
            case ABS_MT_POSITION_X:
                if (touchStage==0)
                {
                    touchStage=1;
                }
                eventStage=1;
                if(180 == recovery_rotation)
                    tmpEvent.x = gr_fb_width() - ev.value;
                else
                    tmpEvent.x = ev.value;
                break;
            case ABS_MT_POSITION_Y:
                if (touchStage==0)
                {
                    touchStage=1;
                }
                eventStage=1;
                if(180 == recovery_rotation)
                    tmpEvent.y = gr_fb_height() - ev.value;
                else
                    tmpEvent.y = ev.value;
                break;
            default :
                break;
        }
        return 1;
    }else if(ev.type == EV_SYN){
        switch(ev.code){
            case SYN_MT_REPORT:
                if (touchStage==1)
                {
                    touchStage=2;
                    firstEvent.x=tmpEvent.x;
                    firstEvent.y=tmpEvent.y;
                    firstEvent.point_id = tmpEvent.point_id;
                    lastEvent.x=tmpEvent.x;
                    lastEvent.y=tmpEvent.y;
                    lastEvent.point_id = tmpEvent.point_id;
                    break;
                }
                if (touchStage==2||touchStage==3)
                {
                    if (tmpEvent.point_id==firstEvent.point_id)
                    {
                        lastEvent.x=tmpEvent.x;
                        lastEvent.y=tmpEvent.y;
                        lastEvent.point_id = tmpEvent.point_id;
                    }
                    break;
                }
                break;
            case SYN_REPORT:
                if (eventStage==1)
                {
                    eventStage=2;
                    if (lastEvent.y-firstEvent.y>touchDistanceThreshold)
                    {
                        touchStage=3;
                        EnqueueKey(KEY_DOWN);
                        firstEvent.y+=touchDistanceThreshold;
                    }
                    if (lastEvent.y-firstEvent.y<-touchDistanceThreshold)
                    {
                        touchStage=3;
                        EnqueueKey(KEY_UP);
                        firstEvent.y-=touchDistanceThreshold;
                    }
                }
                else if (eventStage==2)
                {
                    int deltaY=lastEvent.y-firstEvent.y;
                    if (touchStage==3)
                    {
                        if (deltaY>touchDistanceThreshold)
                            EnqueueKey(KEY_DOWN);
                        else if (deltaY<-touchDistanceThreshold)
                            EnqueueKey(KEY_UP);
                    }
                    else if (touchStage==2)
                    {
                        if (deltaY<7&&deltaY>-7)
                        {
                            //click event
                            int *screenPara=self->GetScreenPara();
                            int lineHeight=screenPara[2]+4;
                            int select=(lastEvent.y-screenPara[0]*lineHeight)/lineHeight;
                            if (select>-1&&select<screenPara[1])
                            {
                                menu_select = select;
                                EnqueueKey(KEY_POWER);
                            }
                        }
                    }
                    touchStage=0;
                    eventStage=0;
                }
                break;
            default:
                break;
        }
        return 1;
    }
    return 0;
}

    /** add by huangtingjin
     *  support handle ir keyevent.
     *
     **/
    int RecoveryUI::ir_handle_input(input_event ev){
        int handled = 0;
        printf("the ev code is %d, value is %d.\n", ev.code, ev.value);
        int ir_keycode = ir_convert_keycode(ev.code);
        if (ev.type == EV_KEY && ev.code <= KEY_MAX && ev.value == 1){
            switch (ir_keycode){
            case IR_KEY_UP:
                //up key
                self->process_key(KEY_UP, 1);
                self->process_key(KEY_UP, 0);
                handled = 1;
                break;
            case IR_KEY_DOWN:
                //down key
                self->process_key(KEY_DOWN, 1);
                self->process_key(KEY_DOWN, 0);
                handled = 1;
                break;
            case IR_KEY_ENTER:
                //enter key
                self->process_key(KEY_ENTER, 1);
                self->process_key(KEY_ENTER, 0);
                handled = 1;
                break;
            case IR_KEY_HOME:
                //enter key
                self->process_key(KEY_HOME, 1);
                self->process_key(KEY_HOME, 0);
                handled = 1;
                break;
            }
        }
        return handled;
}
