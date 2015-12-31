/* 
 * sunxi actuator driver
 */

#include "actuator.h"

#define SUNXI_ACT_NAME "ad5820_act"
#define SUNXI_ACT_ID 0x18

#define TOTAL_STEPS_ACTIVE      64

#define ACT_DEV_DBG_EN 0

//#define USE_SINGLE_LINEAR

//print when error happens
#define act_dev_err(x,arg...) printk(KERN_ERR"[ACT_ERR][ad5820_act]"x,##arg)

//print unconditional, for important info
#if ACT_DEV_DBG_EN == 1
#define act_dev_dbg(x,arg...) printk(KERN_INFO"[ACT][ad5820_act]"x,##arg)
#else
#define act_dev_dbg(x,arg...)
#endif

DEFINE_MUTEX(act_mutex);
//static struct mutex act_mutex;
//declaration
static struct actuator_ctrl_t act_t;
//static struct v4l2_subdev act_subdev;

//static unsigned short subdev_step_pos_table[2*TOTAL_STEPS_ACTIVE];

/*
 Please implement this for each actuator device!!!
*/
static int subdev_i2c_write(struct actuator_ctrl_t *act_ctrl,
                            unsigned short halfword, void* pt)
{
  int ret=0;
  struct i2c_client *client;
  struct i2c_msg msg;
  unsigned char data[2];
  
  client = act_ctrl->i2c_client;
  
  data[0] = (unsigned char)(halfword>>8);
  data[1] = (unsigned char)(0xff&halfword);
  
  //act_dev_dbg("set client->addr=0x%x [0x%x][0x%x, 0x%x]\n",client->addr, halfword, data[0], data[1]);
  
  msg.addr = client->addr;
  msg.flags = 0;
  msg.len = 2;
  msg.buf = data;
  
  ret = i2c_transfer(client->adapter, &msg, 1);
  if (ret > 0) {
    ret = 0;
  }
  else if (ret < 0) {
    act_dev_dbg("subdev_i2c_write write 0x%4x err!\n ",data[0]*256+data[1]);
  }
  
  return ret;
}

static int subdev_set_code(struct actuator_ctrl_t *act_ctrl,
                           unsigned short new_code,
                           unsigned short sr)
{
  int ret=0;
  unsigned short halfword;
  unsigned short last_code;
  unsigned short target_code;
  unsigned short delta_code;
  unsigned short dir;
  unsigned short diff;
  unsigned short range=act_ctrl->active_max-act_ctrl->active_min;
//  act_dev_dbg("subdev_set_code[%d], sr[%d]\n",new_code,sr);
  
  if(act_ctrl->work_status==ACT_STA_BUSY
    ||act_ctrl->work_status==ACT_STA_ERR)
    return -EBUSY;
  
//  if(new_code>act_ctrl->active_max)
//    new_code=act_ctrl->active_max;
//  if(new_code<act_ctrl->active_min)
//    new_code=act_ctrl->active_min;
  
  if(sr>0xf) sr=0xf;
  
  last_code=act_ctrl->curr_code;
  
  if(last_code>new_code) //NEG direction stride
  {
    diff=last_code-new_code;
    dir=0;
  }
  else if(last_code<new_code) //POS direct
  {
    diff=new_code-last_code;
    dir=1;
  }
  else
  {
    return 0;
  }
  act_dev_dbg("act step from[%d] to [%d] range[%d] sr[%d]\n", last_code, new_code, range ,sr);
  
  act_ctrl->work_status=ACT_STA_BUSY;
  target_code=last_code;//start from last code

#ifdef USE_SINGLE_LINEAR
  ret=subdev_i2c_write(act_ctrl, new_code<<4|0x1, NULL);
#else
  if(dir==0) //NEG dir
  {
    if( (diff>=1) && (new_code<=(act_ctrl->active_min+range/8)) &&
                     (last_code>=(act_ctrl->active_min+range/6)) )
    {
      int delay;
      if(target_code>=act_ctrl->active_max-range/5)
        delay=30;
      else if(target_code>=(act_ctrl->active_min+range/6))
        delay=20;
      else
        delay=10;
      
      sr=0xf;
      target_code=range/8+act_ctrl->active_min;
      act_dev_dbg("act NEG min step to [%d]\n", target_code);
      halfword = (target_code&0x3ff)<<4 | (sr&0xf);
      ret=subdev_i2c_write(act_ctrl, halfword, NULL);
      if(ret!=0)
      {
        act_dev_dbg("act step target error\n");
        goto set_code_ret;
      }
      else
      {
        act_ctrl->curr_code=target_code;
      }
      usleep_range(delay*1000,delay*1100);
    }
    else if(diff>(range/6) && new_code<act_ctrl->active_min+(range/6))//
    {
      int loop=0;
      sr=0x3;
      if(diff>(range/3))
        delta_code=(diff+1)/3;
      else
        delta_code=(diff+1)/2;
      while(target_code>new_code)
      {
        loop++;
        if(loop>=3)
        {
          act_dev_dbg("loop=3\n");
          break;
        }
        if(target_code>delta_code)
          target_code=(target_code-delta_code);
        else
          break;
        
        if( target_code<=new_code || target_code<=delta_code )
          break;
        
        act_dev_dbg("act NEG step to [%d]\n", target_code);
        halfword = (target_code&0x3ff)<<4 | (sr&0xf);
        ret=subdev_i2c_write(act_ctrl, halfword, NULL);
        if(ret!=0)
        {
          act_dev_dbg("act NEG step target error\n");
          goto set_code_ret;
        }
        else
        {
          act_ctrl->curr_code=target_code;
        }
        usleep_range(10000,12000);
      }
    }
    //last step
    {
      sr=0x3;
      act_dev_dbg("act NEG target to [%d]\n", new_code);
      halfword = (new_code&0x3ff)<<4 | (sr&0xf);
      ret=subdev_i2c_write(act_ctrl, halfword, NULL);
    }
  }
  else//POS dir
  {
    if( (diff>range/5) &&
        (new_code>=(act_ctrl->active_max-(range/10))) &&
        (last_code<=(act_ctrl->active_min-(range/6))) )// 
    {
      int delay;
      if(new_code>=(act_ctrl->active_max-range/10))
        delay=10;
      else
        delay=20;
      
      sr=0xf;
      if(new_code>(act_ctrl->active_max-range/10) )
        target_code=act_ctrl->active_max-range/10;
      else
        target_code=new_code;
      act_dev_dbg("act POS max step to [%d]\n", target_code);
      halfword = (target_code&0x3ff)<<4 | (sr&0xf);
      ret=subdev_i2c_write(act_ctrl, halfword, NULL);
      if(ret!=0)
      {
        act_dev_dbg("act POS step target error\n");
        goto set_code_ret;
      }
      else
      {
        act_ctrl->curr_code=target_code;
      }
      usleep_range(delay*1000,delay*1100);
    }
    if(target_code<new_code)
    {
      sr=0x1;
      act_dev_dbg("act POS target to [%d]\n", new_code);
      halfword = (new_code&0x3ff)<<4 | (sr&0xf);
      ret=subdev_i2c_write(act_ctrl, halfword, NULL);
    }
  }
#endif
  
set_code_ret:
  act_ctrl->work_status=ACT_STA_HALT;
  if(ret!=0)
    act_dev_err("subdev set code err!\n");
  else
    act_ctrl->curr_code = new_code;
  
  return ret;
}

static int subdev_init_table(struct actuator_ctrl_t *act_ctrl,
                             unsigned short ext_tbl_en,
                             unsigned short ext_tbl_steps,
                             unsigned short * ext_tbl)
{
  int ret=0;
  unsigned int i;
  unsigned short *table;
  unsigned short table_size;
  
  act_dev_dbg("subdev_init_table\n");
  
  if(ext_tbl_en==0)
  {
    //table = subdev_step_pos_table;
    //printk("sizeof(subdev_step_pos_table)=%d\n",sizeof(subdev_step_pos_table));
    table_size=2*TOTAL_STEPS_ACTIVE*sizeof(unsigned short);
    table = (unsigned short *)kmalloc(table_size, GFP_KERNEL);
    
    for(i=0;i<TOTAL_STEPS_ACTIVE;i+=1)
    {
      table[2*i]=table[2*i+1]=(unsigned short)(act_ctrl->active_min+
      (unsigned int)(act_ctrl->active_max-act_ctrl->active_min)*i/(TOTAL_STEPS_ACTIVE-1));
      
    }
    act_ctrl->total_steps=TOTAL_STEPS_ACTIVE;
  }
  else if(ext_tbl_en==1)
  {
    table = (unsigned short *)kmalloc(2*ext_tbl_steps*sizeof(unsigned short), GFP_KERNEL);
    for(i=0;i<ext_tbl_steps;i+=1)
    {
      table[2*i]=ext_tbl[2*i];
      table[2*i+1]=ext_tbl[2*i+1];
    }
    act_ctrl->total_steps=ext_tbl_steps;
  }
  else
    return 0;
  
  act_ctrl->step_position_table = table;
//  for(i=0;i<TOTAL_STEPS_ACTIVE;i+=1)
//  {
//    act_dev_dbg("TBL[%d]=%d [%d]=%d ",i+0, table[2*i+0], i+1, table[2*i+1]);
//  }
  
  return ret;
}

static int subdev_move_pos(struct actuator_ctrl_t *act_ctrl,
                           unsigned short num_steps,
                           unsigned short dir)
{
  int ret = 0;
  char sign_dir = 0;
//  unsigned short index = 0;
  unsigned short target_pos = 0;
//  unsigned short target_code = 0;
  short dest_pos = 0;
  unsigned short curr_code = 0;
  act_dev_dbg("%s called, dir %d, num_steps %d\n",
    __func__,
    dir,
    num_steps);

  /* Determine sign direction */
  if (dir == MOVE_NEAR)
    sign_dir = 1;
  else if (dir == MOVE_FAR)
    sign_dir = -1;
  else {
    act_dev_err("Illegal focus direction\n");
    ret = -EINVAL;
    return ret;
  }

  /* Determine destination step position */
  dest_pos = act_ctrl->curr_pos +
    (sign_dir * num_steps);

  if (dest_pos < 0)
    dest_pos = 0;
  else if (dest_pos > act_ctrl->total_steps-1)
    dest_pos = act_ctrl->total_steps-1;

  if (dest_pos == act_ctrl->curr_pos)
    return ret;
  
  
  act_ctrl->work_status=ACT_STA_BUSY;
  
  curr_code = act_ctrl->step_position_table[dir+2*act_ctrl->curr_pos];
  
  act_dev_dbg("curr_pos =%d dest_pos =%d curr_code=%d\n",
    act_ctrl->curr_pos, dest_pos, curr_code);
  
  target_pos =  act_ctrl->curr_pos;
  while (target_pos != dest_pos) {
    target_pos++;
    ret=subdev_set_code(act_ctrl,act_ctrl->step_position_table[dir+2*target_pos],0);
    if(ret==0)
    {
      msleep(1);
      act_ctrl->curr_pos = target_pos;
    }
    else
    {
      break;
    }
  }

  act_ctrl->work_status=ACT_STA_IDLE;
  
  return ret;
}

static int subdev_set_pos(struct actuator_ctrl_t *act_ctrl,
                          unsigned short pos)
{
  int ret = 0;
  unsigned short target_pos = 0;
  
  /* Determine destination step position */
  if(pos > act_ctrl->total_steps - 1)
    target_pos = act_ctrl->total_steps - 1;
  else 
    target_pos = pos;
  
  //act_dev_dbg("subdev_set_pos[%d]\n",target_pos);
  
  if (target_pos == act_ctrl->curr_pos)
    return ret;
  
  ret=subdev_set_code(act_ctrl,act_ctrl->step_position_table[2*target_pos],0);
  if(ret==0)
  {
    msleep(1);
    act_ctrl->curr_pos = target_pos;
  }
  else
  {
    act_dev_err("act set pos err!");
  }

  return ret;
}

static int subdev_init(struct actuator_ctrl_t *act_ctrl,
                       struct actuator_para_t *a_para)
{
  int ret=0;
  struct actuator_para_t *para=a_para;
//  struct v4l2_subdev *sdev = &act_ctrl->sdev;
  act_dev_dbg("act subdev_init\n");
  mutex_init(act_ctrl->actuator_mutex);
  
  if(a_para==NULL)
  {
    act_dev_err("subdev_init para error\n");
    ret = -1;
    goto subdev_init_end;
  }
  
  act_ctrl->active_min=para->active_min;
  act_ctrl->active_max=para->active_max;
  
  //init_table
  subdev_init_table(act_ctrl, para->ext_tbl_en, para->ext_tbl_steps, para->ext_tbl);
  
  act_ctrl->curr_pos = 0;
  act_ctrl->curr_code = 0;
  
//  ret=subdev_i2c_write(act_ctrl,act_ctrl->active_min, NULL);
  ret=subdev_set_code(act_ctrl,act_ctrl->active_min, 0);
  
  if(ret==0)
    act_ctrl->work_status=ACT_STA_IDLE;
  else
    act_ctrl->work_status=ACT_STA_ERR;
  
subdev_init_end:
  return ret;
}

static int subdev_pwdn(struct actuator_ctrl_t *act_ctrl,
                       unsigned short mode)
{
  int ret=0;
//  struct v4l2_subdev *sdev = &act_ctrl->sdev;
  if(mode==1)
  {
    act_dev_dbg("act subdev_pwdn %d\n",mode);
    act_ctrl->work_status=ACT_STA_HALT;
    //ret=subdev_i2c_write(act_ctrl,1<<15|0xf, NULL);
    ret=subdev_set_code(act_ctrl,0x0000,0x4);
    usleep_range(10000,12000);
    ret=subdev_i2c_write(act_ctrl,0x8000,0);
    //if(ret==0)
      act_ctrl->work_status=ACT_STA_SOFT_PWDN;
  }
  else
  {
    act_dev_dbg("act subdev_pwdn %d\n",mode);
  //if gpio control 
  //if(act_ctrl->af_io!=NULL)
    //setgpio
    ret=0;
    act_ctrl->work_status=ACT_STA_HW_PWDN;
  }
  
  return ret;
}

static int subdev_release(struct actuator_ctrl_t *act_ctrl,
                          struct actuator_ctrl_word_t *ctrlwd)
{
  int ret=0;
  act_dev_dbg("act subdev_release[%d] to [%d], sr[%d]\n", act_ctrl->curr_code,ctrlwd->code,ctrlwd->sr);
  
  if(act_ctrl->work_status!=ACT_STA_HALT)
    return 0;
  
  ret=subdev_set_code(act_ctrl, act_ctrl->active_min, ctrlwd->sr);//set to min code
  if(ret==0)
  {
    act_dev_dbg("release ok!\n");
    act_ctrl->work_status=ACT_STA_IDLE;
  }
  act_dev_dbg("release finished\n");
  return ret;
}

static const struct i2c_device_id act_i2c_id[] = {
  {SUNXI_ACT_NAME, (kernel_ulong_t)&act_t},
  { }
};

static const struct v4l2_subdev_core_ops sunxi_act_subdev_core_ops = {
  .ioctl = sunxi_actuator_ioctl,//extracted in actuator.c
};

static struct v4l2_subdev_ops act_subdev_ops = {
  .core = &sunxi_act_subdev_core_ops,
  //no other ops
};


static int act_i2c_probe(struct i2c_client *client,
      const struct i2c_device_id *id)
{
  v4l2_i2c_subdev_init(&act_t.sdev, client, &act_subdev_ops);
  act_t.i2c_client=client;
  act_t.work_status=ACT_STA_HW_PWDN;
  //add act other init para
  
  act_dev_dbg("%s probe\n",SUNXI_ACT_NAME);
  return 0;
}

static int act_i2c_remove(struct i2c_client *client)
{
  struct v4l2_subdev *sd = i2c_get_clientdata(client);
  v4l2_device_unregister_subdev(sd);
  return 0;
}

static struct i2c_driver act_i2c_driver = {
  .driver = {
    .owner = THIS_MODULE,
    .name = SUNXI_ACT_NAME,
  },
  .id_table = act_i2c_id,
  .probe  = act_i2c_probe,//actuator_i2c_probe,//,
  .remove = act_i2c_remove,//actuator_i2c_remove,//,
};

static struct actuator_ctrl_t act_t = {
  .i2c_driver = &act_i2c_driver,
  //.i2c_addr = SUNXI_ACT_ID,
  //.sdev
  .sdev_ops = &act_subdev_ops,
  
  .set_info = {
    .total_steps = TOTAL_STEPS_ACTIVE,
  },

  .work_status = ACT_STA_HW_PWDN,
  .active_min = 100,//ACT_DEV_MIN_CODE,
  .active_max = 600,//ACT_DEV_MAX_CODE,
  .curr_pos = 0,
  .curr_code = 0,
  
  .actuator_mutex = &act_mutex,

  .func_tbl = {
    //.actuator_ioctl = subdev_ioctl,
    
    //specific function
    .actuator_init = subdev_init,
    .actuator_pwdn = subdev_pwdn,
    .actuator_i2c_write = subdev_i2c_write,
    .actuator_release = subdev_release,
    .actuator_set_code = subdev_set_code,
    
    .actuator_init_table = subdev_init_table,
    .actuator_move_pos = subdev_move_pos,
    .actuator_set_pos = subdev_set_pos,
  },

  .get_info = {//just for example
    .focal_length_num = 42,
    .focal_length_den = 10,
    .f_number_num = 265,
    .f_number_den = 100,
    .f_pix_num = 14,
    .f_pix_den = 10,
    .total_f_dist_num = 197681,
    .total_f_dist_den = 1000,
  },
};

static int __init act_mod_init(void)
{
  int ret=0;
  ret = i2c_add_driver(&act_i2c_driver);
  //act_dev_dbg("act_mod_init[%s]=%d\n", act_i2c_driver.driver.name, ret);
  return ret;
}

static void __exit act_mod_exit(void)
{
  //act_dev_dbg("act_mod_exit[%s]\n",act_i2c_driver.driver.name);
  i2c_del_driver(&act_i2c_driver);
}

module_init(act_mod_init);
module_exit(act_mod_exit);

MODULE_DESCRIPTION("ad5820 vcm actuator");
MODULE_LICENSE("GPL v2");
