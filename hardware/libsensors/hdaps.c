/**
 * HDAPS accelerometer sensor
 *
 * Copyright (C) 2011 The Android-x86 Open Source Project
 *
 * by Stefan Seidel <stefans@android-x86.org>
 * Adaptation by Tanguy Pruvot <tpruvot@github>
 *
 * Licensed under GPLv2 or later
 *
 **/

/* #define LOG_NDEBUG 0 */
#define LOG_TAG "HdapsSensors"

#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <fcntl.h>
#include <errno.h>
#include <dirent.h>
#include <math.h>
#include <poll.h>
#include <pthread.h>
#include <linux/input.h>

#include <cutils/atomic.h>
#include <cutils/log.h>
#include <cutils/properties.h>
#include <hardware/sensors.h>

#define INPUT_DIR		"/dev/input"
#define ARRAY_SIZE(a)		(sizeof(a) / sizeof(a[0]))
#define ID_ACCELERATION		(SENSORS_HANDLE_BASE + 0)

#define AMIN(a,b)		(((a)<(fabs(b)))?(a):(b))
#define SQUARE(x)		((x)*(x))
#define COS_ASIN(m,x)		(sqrt(SQUARE(m)-SQUARE(AMIN(m,x))))
#define COS_ASIN_2D(m,x,y)	(COS_ASIN(m,x)*COS_ASIN(m,y)/(m))

typedef struct {
	const char *name;
	float conv[3];
	int swap[3];
	int avg_cnt;
} accel_params;

/* precision, result data should give GRAVITY_EARTH ~9.8 */
#define CONVERT			(GRAVITY_EARTH / 156.0f)
#define CONVERT_PEGA		(GRAVITY_EARTH / 256.0f)
#define CONVERT_LIS		(GRAVITY_EARTH / 1024.0f)

/* axis swap for tablet pcs, X=0, Y=1, Z=2 */
#define NO_SWAP			{ 0, 1, 2 }
#define SWAP_YXZ		{ 1, 0, 2 }
#define SWAP_ZXY		{ 2, 0, 1 }

static accel_params accel_list[] = {
	{ "hdaps", { CONVERT, -CONVERT, 0 }, NO_SWAP, 1 },
	{ "Pegatron Lucid Tablet Accelerometer", { CONVERT_PEGA, CONVERT_PEGA, CONVERT_PEGA }, NO_SWAP, 4 },
//	{ "ST LIS3LV02DL Accelerometer",  { -CONVERT_LIS, CONVERT_LIS, CONVERT_LIS }, SWAP_YXZ, 2 }, /* tablet mode */
	{ "ST LIS3LV02DL Accelerometer",  { CONVERT_LIS, -CONVERT_LIS, CONVERT_LIS }, SWAP_ZXY, 2 }, /* pc mode */
};

static accel_params accelerometer;
static sensors_vec_t *events_q = NULL;
static int event_cnt = 0;
static unsigned int forced_delay = 0;

struct sensors_poll_context_t {
	struct sensors_poll_device_t device;
	int fd;
};

static int common__close(struct hw_device_t *dev) {
	struct sensors_poll_context_t *ctx = (struct sensors_poll_context_t *) dev;
	free(ctx);
	free(events_q);
	events_q = NULL;

	return 0;
}

static int device__activate(struct sensors_poll_device_t *dev, int handle,
		int enabled) {

	return 0;
}

static int device__set_delay(struct sensors_poll_device_t *device, int handle,
		int64_t ns) {
	forced_delay = ns / 1000;
	return 0;

}

static int device__poll(struct sensors_poll_device_t *device,
		sensors_event_t *data, int count) {

	struct input_event event;
	struct sensors_poll_context_t *dev =
			(struct sensors_poll_context_t *) device;

	accel_params signs;
	char prop[PROPERTY_VALUE_MAX] = "";
	float val;
	int x, y, z;

	if (dev->fd < 0)
		return 0;

	// dynamic axis tuning, expect "1,-1,-1" format
	if (property_get("hal.sensors.axis.revert", prop, 0)) {
		sscanf(prop, "%d,%d,%d", &x, &y, &z);
		ALOGD("axis signs set to %d %d %d", x, y, z);
	} else {
		x = y = z = 1;
	}
	signs.conv[ABS_X] = accelerometer.conv[ABS_X] * x;
	signs.conv[ABS_Y] = accelerometer.conv[ABS_Y] * y;
	signs.conv[ABS_Z] = accelerometer.conv[ABS_Z] * z;

	ALOGV("axis convert set to %.6f %.6f %.6f",
		signs.conv[ABS_X], signs.conv[ABS_Y] ,signs.conv[ABS_Z]);

	// dynamic axis swap, expect "0,1,2" format
	if (property_get("hal.sensors.axis.order", prop, 0)) {
		sscanf(prop, "%d,%d,%d", &x, &y, &z);
		ALOGD("axis order set to %c %c %c", 'x'+x, 'x'+y, 'x'+z);
	} else {
		// use default values (accel_params)
		x = accelerometer.swap[0];
		y = accelerometer.swap[1];
		z = accelerometer.swap[2];
	}

	while (read(dev->fd, &event, sizeof(event)) > 0) {

		ALOGV("gsensor event %d - %d - %d", event.type, event.code, event.value);

		if (event.type == EV_ABS) {
			switch (event.code) {
			// Even though this mapping results in wrong results with some apps,
			// it just means that these apps are broken, i.e. they rely on the
			// fact that phone have portrait displays. Laptops/tablets however
			// have landscape displays, and the axes are relative to the default
			// screen orientation, not relative to portrait orientation.
			// See the nVidia Tegra accelerometer docs if you want to know for sure.
			case ABS_X: // 0x00
			case ABS_Y: // 0x01
			case ABS_Z: // 0x02
				val = signs.conv[event.code] * event.value;
				if (event.code == ABS_X)
					events_q[event_cnt].v[x] = val;
				else if (event.code == ABS_Y)
					events_q[event_cnt].v[y] = val;
				else if (event.code == ABS_Z)
					events_q[event_cnt].v[z] = val;
				break;
			}
		} else if (event.type == EV_SYN) {
			int i;
			data->timestamp = (int64_t) ((int64_t) event.time.tv_sec
					* 1000000000 + (int64_t) event.time.tv_usec * 1000);
			// hdaps doesn't have z-axis, so simulate it by rotation matrix solution
			if (signs.conv[2] == 0)
				events_q[event_cnt].z = COS_ASIN_2D(GRAVITY_EARTH, events_q[event_cnt].x, events_q[event_cnt].y);
			memset(&data->acceleration, 0, sizeof(sensors_vec_t));
			for (i = 0; i < accelerometer.avg_cnt; ++i) {
				data->acceleration.x += events_q[i].x;
				data->acceleration.y += events_q[i].y;
				data->acceleration.z += events_q[i].z;
			}
			data->sensor = ID_ACCELERATION;
			data->type = SENSOR_TYPE_ACCELEROMETER;
			data->acceleration.status = SENSOR_STATUS_ACCURACY_HIGH;
			if (++event_cnt >= accelerometer.avg_cnt)
				event_cnt = 0;

			// spare the CPU if desired
			if (forced_delay)
				usleep(forced_delay);
			return 1;
		}
	}

	return -errno;
}

static int open_input_device(void) {
	char *filename;
	int fd = -1;
	DIR *dir;
	struct dirent *de;
	char name[80];
	char devname[256];
	dir = opendir(INPUT_DIR);
	if (dir == NULL)
		return -1;

	strcpy(devname, INPUT_DIR);
	filename = devname + strlen(devname);
	*filename++ = '/';

	while ((de = readdir(dir))) {
		int f;
		size_t i;
		if (de->d_name[0] == '.')
			continue;
		strcpy(filename, de->d_name);
		f = open(devname, O_RDONLY);
		if (f < 0) {
			continue;
		}

		if (ioctl(f, EVIOCGNAME(sizeof(name) - 1), &name) < 1) {
			name[0] = '\0';
		}

		ALOGV("%s name is %s", devname, name);

		for (i = 0; i < ARRAY_SIZE(accel_list); ++i) {
			if (!strcmp(name, accel_list[i].name)) {
				int c = accel_list[i].avg_cnt;
				memcpy(&accelerometer, &accel_list[i], sizeof(accel_params));
				accelerometer.conv[0] = accel_list[i].conv[0] / c;
				accelerometer.conv[1] = accel_list[i].conv[1] / c;
				accelerometer.conv[2] = accel_list[i].conv[2] / c;
				events_q = calloc(c, sizeof(sensors_vec_t));
				ALOGI("found %s at %s", name, devname);
				break;
			}
		}
		if (events_q) {
			fd = f;
			break;
		}
		close(f);
	}
	closedir(dir);

	return fd;
}

static const struct sensor_t sSensorList[] = {
	{	.name = "HDAPS accelerometer",
		.vendor = "Linux kernel",
		.version = 1,
		.handle = ID_ACCELERATION,
		.type = SENSOR_TYPE_ACCELEROMETER,
		.maxRange = (GRAVITY_EARTH * 6.0f),
		.resolution = (GRAVITY_EARTH * 6.0f) / 1024.0f,
		.power = 0.84f,
		.reserved = {},
	},
};

static int open_sensors(const struct hw_module_t* module, const char* name,
		struct hw_device_t** device);

static int sensors__get_sensors_list(struct sensors_module_t* module,
		struct sensor_t const** list) {
	*list = sSensorList;

	return ARRAY_SIZE(sSensorList);
}

static struct hw_module_methods_t sensors_module_methods = {
	.open = open_sensors
};

struct sensors_module_t HAL_MODULE_INFO_SYM = {
	.common = {
		.tag = HARDWARE_MODULE_TAG,
		.version_major = 2,
		.version_minor = 0,
		.id = SENSORS_HARDWARE_MODULE_ID,
		.name = "hdaps accelerometer sensor",
		.author = "Stefan Seidel",
		.methods = &sensors_module_methods,
		.dso = NULL,
		.reserved = {},
	},
	.get_sensors_list = sensors__get_sensors_list
};

static int open_sensors(const struct hw_module_t* module, const char* name,
		struct hw_device_t** device) {
	int status = -EINVAL;

	struct sensors_poll_context_t *dev = calloc(1,
			sizeof(struct sensors_poll_context_t));

	dev->device.common.tag = HARDWARE_DEVICE_TAG;
	dev->device.common.version = 0;
	dev->device.common.module = (struct hw_module_t*) module;
	dev->device.common.close = common__close;
	dev->device.activate = device__activate;
	dev->device.setDelay = device__set_delay;
	dev->device.poll = device__poll;

	if ((dev->fd = open_input_device()) < 0) {
		ALOGE("GSensor get class path error");
	} else {
		*device = &dev->device.common;
		status = 0;
	}

	return status;
}
