/*
 * \file        2gtester.c
 * \brief       just an example.
 *
 * \version     1.0.0
 * \date        2012年05月31日
 * \author      martin <zhengjiewen@allwinnertech.com>
 *
 * Copyright (c) 2012 Allwinner Technology. All Rights Reserved.
 *
 */

/* include system header */
#include <stdio.h>
#include <string.h>
#include <pthread.h>
#include <ctype.h>
#include <stdlib.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>
#include<termios.h>
#include "asoundlib.h"
/* include "dragonboard_inc.h" */
#include "dragonboard_inc.h"

#define MAX_AT_RESPONSE 0x1000
int device_fd;

static char s_ATBuffer[MAX_AT_RESPONSE];
/*static void tinymix_set_value(struct mixer *mixer, unsigned int id,
                              unsigned int value);
*/
static int writeline (const char *s)
{
	int cur = 0;
	int len = strlen(s);
	int written;

	db_msg("loop:prepare to writeline....\n");
	if (device_fd < 0) {
		return -1;
	}

	/* the main string */
	while (cur < len) {
		do {
			written = write (device_fd, s + cur, len - cur);
		} while (written < 0 && errno == EINTR);

		if (written < 0) {
			return -1;
		}
		cur += written;
	}

	/* the \r  */

	do {
		written = write (device_fd, "\r" , 1);
	} while ((written < 0 && errno == EINTR) || (written == 0));

	if (written < 0) {
		db_error("2glooptester:write \\r error\n");
		return -1;
	}
	db_msg("loop:writeline finish....\n");
	return 0;
}

static const char *readline()
{
	int count;
	db_msg("loop:prepare to readline....\n");
	do{
		count = read(device_fd, s_ATBuffer,MAX_AT_RESPONSE);
		db_msg("readline count=%d\n",count);		         
	} while (count < 0 && errno == EINTR);

	if (count > 0){ 
		s_ATBuffer[count] = '\0';//mark the string end
	}
	else if (count<=0) {
		/* read error encountered or EOF reached */
		if(count == 0) {
			db_error("ATchannel: EOF reached\n");
		} else {
			db_error("ATchannel: read error %s\n", strerror(errno));
		}
		return NULL;
	}
	return s_ATBuffer;
}

//in setup_2g_device,we do three things:
//first: delay sometime to ensure the 2g device setup properly
//second: open the device node
//third: sent "AT+CSQ" command to query the single level and return it

static int setup_device(char *device_node_path,int setup_delay)
{
	struct termios  ios;

	device_fd= open (device_node_path, O_RDWR);
	if(device_fd<0){
		db_error("2glooptester:open device error\n");
	}

	/* disable echo on serial ports */
	tcgetattr( device_fd, &ios );
	ios.c_lflag = 0;  

	tcflush(device_fd, TCIFLUSH);
	cfsetospeed(&ios,B115200);
	if(tcsetattr( device_fd, TCSANOW, &ios )){
		db_error("set tty attr fail\n");
		return -1;
	}
	
	db_msg("sleep %d s to wait for hardware ready\n",setup_delay);
	sleep(setup_delay);//sleep befor hardware get ready

	return 0;
}

static void tinymix_set_value(struct mixer *mixer, unsigned int id,
                              unsigned int value)
{
	struct mixer_ctl *ctl;
	enum mixer_ctl_type type;
	unsigned int num_values;
	unsigned int i;

	ctl = mixer_get_ctl(mixer, id);
	type = mixer_ctl_get_type(ctl);
	num_values = mixer_ctl_get_num_values(ctl);

	for (i = 0; i < num_values; i++) {
		if (mixer_ctl_set_value(ctl, i, value)) {
			fprintf(stderr, "Error: invalid value\n");
			return;
		}
	}
}

static int loop_test(void)
{
	struct mixer *mixer;
	int card = 0;
	mixer = mixer_open(card);
	if (!mixer) {
		fprintf(stderr, "Failed to open mixer\n");
		return EXIT_FAILURE;
	}
	tinymix_set_value(mixer, 92, 1);
	tinymix_set_value(mixer, 93, 1);
	tinymix_set_value(mixer, 95, 1);
	tinymix_set_value(mixer, 100, 1);
	tinymix_set_value(mixer, 0, 60);
	tinymix_set_value(mixer, 34,7);
	tinymix_set_value(mixer, 18,7);
	mixer_close(mixer);
	return 0;
}

static int enter_loop_mode(void)
{   
	char *p_cur;
	int try=0;
	if(writeline("AT+ECHO1ON")){
	    return -1;
	}
	sleep(2);
	while(try<3){
		if(!readline()){
			 sleep(2);
			  try++;  
		} 
		else {
			break;
		}
	}
	db_msg("2glooptester ATD readline: %s\n",s_ATBuffer);
	if(try>=3) {
		db_error("2glooptester:no correct response for the ATD\n");
		return -1;
	}
	 p_cur=strstr(s_ATBuffer,"OK");
	if(p_cur){
		return 0;
	}
	else {
	    return -1;    
	}
}

static int  exit_loop_mode()
{
	struct mixer *mixer;
	int card = 0;
	mixer = mixer_open(card);
	if (!mixer) {
		fprintf(stderr, "Failed to open mixer\n");
		return EXIT_FAILURE;
	}
	tinymix_set_value(mixer, 92, 0);
	tinymix_set_value(mixer, 93, 0);
	tinymix_set_value(mixer, 95, 0);
	tinymix_set_value(mixer, 100, 0);
	mixer_close(mixer);
	if(writeline("AT+ECHO1OFF")){
		return -1;
	}
	return 0;
}

/* C entry.
 *
 * \param argc the number of arguments.
 * \param argv the arguments.
 *
 * DO NOT CHANGES THE NAME OF PARAMETERS, otherwise your program will get 
 * a compile error if you are using INIT_CMD_PIPE macro.
 */
int main(int argc, char *argv[])
{
	char device_node_path[256];
	int setup_delay;
	int call_time;
	int loop_ctl_pipe_fd;
	int loop_mode_flag = 1;
	int ret = -1;

	/* init cmd pipe after local variable declaration */
	INIT_CMD_PIPE();

	init_script(atoi(argv[2]));
	db_msg("2glooptester\n");
	if(script_fetch("loop","device_node",(int*)device_node_path,sizeof(device_node_path)/4)){
		db_warn("2glooptester:can't fetch device_node,set to /dev/ttyUSB0\n");
		strcpy(device_node_path,"/dev/ttyUSB0");
	}
	db_msg("device_node=%s\n",device_node_path);

	if(script_fetch("loop","setup_delay",&setup_delay,1)){
		db_warn("2glooptester:can't fetch setup_delay,set to 5s\n");
		setup_delay=5;
	}

	if(script_fetch("loop","call_time",&call_time,1)){
		db_warn("2glooptester:can't fetch call_time,set to 30s\n");
		call_time=30;
	}
/*	single_level=setup_device(device_node_path,setup_delay);
	if(single_level==0)
	{
		SEND_CMD_PIPE_FAIL_EX("can't get single level");
		return -1;
	}*/
	
	loop_ctl_pipe_fd = open(LOOP_CTL_PIPE_NAME,O_RDONLY ,0); 					
	if(loop_ctl_pipe_fd==NULL){
		printf("2glooptester:fail to open loop_ctl_pipe\n");
	}
	setup_device(device_node_path,setup_delay);
LOOP_TEST:
	if(enter_loop_mode()){ 
		SEND_CMD_PIPE_FAIL_EX("failed:loop\n");
		return 0;
	}
	if(loop_test()){
		SEND_CMD_PIPE_FAIL_EX("failed:sound_loop\n");
		return 0;
	}
	SEND_CMD_PIPE_OK();

	//call time delay,you can speak th each other in this time
//	sleep(call_time);
//	exit_loop_mode();
	/* send OK to core if test OK, otherwise send FAIL
	 * by using SEND_CMD_PIPE_FAIL().
	 */
	SEND_CMD_PIPE_OK_EX("test done");
	while(1){
		db_msg("get loop_ctl_pipe\n");
		ret = read(loop_ctl_pipe_fd,&loop_mode_flag,sizeof(loop_mode_flag));
		if(ret == -1){
			db_msg("no data avlaible\n");
		}
		db_msg("loop mode ON \n");
		if(!loop_mode_flag){
				db_msg("exit_loop_mode\n");
				exit_loop_mode();
				break;
		}
		sleep(1);
	}
	while(1){
		ret = read(loop_ctl_pipe_fd,&loop_mode_flag,sizeof(loop_mode_flag));
		if(ret == -1){
			db_msg("no data avlaible\n");
		}
		db_msg("loop mode OFF \n");
		sleep(2);
		if(loop_mode_flag)
			goto LOOP_TEST;
	}
	close(loop_ctl_pipe_fd);
	return 0;
}
