/*
 * w500_sensor.c
 *
 *      Created on: 28.05.2012
 *      Author: Marek Vasut <marex@denx.de>
 *      Licensed under GPLv2 or later
 */

#define LOG_TAG "W500Sensors"

#include <linux/types.h>
#include <linux/input.h>
#include <fcntl.h>
#include <cutils/sockets.h>
#include <cutils/log.h>
#include <cutils/native_handle.h>
#include <dirent.h>
#include <math.h>
#include <hardware/sensors.h>

#define DRIVER_DESC 			"Acer BMA150 accelerometer"
#define SENS_COUNT			3
#define SENS_LIGHT			0
#define SENS_ROTATE			1
#define SENS_ACCEL			2
#define ID_AMBIENT_LIGHT		(SENSORS_HANDLE_BASE + SENS_LIGHT)
#define ID_ROTATION_VECTOR		(SENSORS_HANDLE_BASE + SENS_ROTATE)
#define ID_ACCELEROMETER		(SENSORS_HANDLE_BASE + SENS_ACCEL)

#define VALID_LIGHT			(1 << 0)
#define VALID_ROTATE			(1 << 1)
#define VALID_ACCEL			(1 << 2)

#define VALID_ROTATE_X			(1 << 0)
#define VALID_ROTATE_Y			(1 << 1)
#define VALID_ROTATE_Z			(1 << 2)
#define VALID_ROTATE_MASK		(7 << 0)

struct sensor_context {
	struct sensors_poll_device_t device;
	int accel_fd;

	struct timespec delay;

	uint32_t sent;
	uint32_t valid;

	int light_data;
	int orientation_data[3];
	uint8_t orientation_valid;
};

static int open_accel_sensor(void)
{
	const char *dirname = "/dev/input";
	DIR *dir;
	struct dirent *de;
	char name[PATH_MAX];
	int ret;
	int fd;

	fd = -1;
	dir = opendir(dirname);
	if (dir != NULL) {
		/*
		 * Loop over all "eventXX" in /dev/input and look
		 * for our driver.
		 */
		ALOGD("%s[%i] Looping over all eventXX...", __func__, __LINE__);
		while ((de = readdir(dir))) {
			if (de->d_name[0] != 'e')
				continue;
			memset(name, 0, PATH_MAX);
			snprintf(name, PATH_MAX, "%s/%s", dirname, de->d_name);
			ALOGD("%s[%i] Open device %s", __func__, __LINE__, name);

			fd = open(name, O_RDWR);
			if (fd < 0) {
				ALOGD("%s[%i] Could not open %s, %s", __func__,
					__LINE__, name, strerror(errno));
				continue;
			}

			name[PATH_MAX - 1] = '\0';
			ret = ioctl(fd, EVIOCGNAME(PATH_MAX - 1), &name);
			if (ret < 1) {
				ALOGD("%s[%i] Could not get device name for "
					"%s, %s\n", __func__, __LINE__,
					name, strerror(errno));
				name[0] = '\0';
			}

			ALOGI("%s[%i] Testing device %s",
					__func__, __LINE__, name);

			if (!strcmp(name, DRIVER_DESC)) {
				ALOGI("%s[%i] Found device %s",
					__func__, __LINE__, name);
				break;
			}

			close(fd);
		}

		ALOGD("%s[%i] stop loop and closing directory",
			__func__, __LINE__);
		closedir(dir);
	}

	return fd;
}

static int context__activate(struct sensors_poll_device_t *dev,
				int handle, int enabled)
{
	struct sensor_context* ctx = (struct sensor_context *)dev;
	int fd;

	switch (handle) {
	case SENS_ROTATE:
		if (ctx->accel_fd >= 0)
			return 0;

		fd = open_accel_sensor();
		if (fd < 0)
			return -EINVAL;

		ctx->accel_fd = fd;
		return 0;

	case SENS_LIGHT:
	case SENS_ACCEL:
		/* Light and fake accel need no activation. */
		return 0;
	}

	return -EINVAL;
}

static int context__setDelay(struct sensors_poll_device_t *dev,
				int handle, int64_t ns)
{
	struct sensor_context* ctx = (struct sensor_context *)dev;

	ctx->delay.tv_sec = 0;
	ctx->delay.tv_nsec = ns;

	return 0;
}

static int context__close(struct hw_device_t *dev)
{
	struct sensor_context* ctx = (struct sensor_context *)dev;

	close(ctx->accel_fd);

	free(ctx);

	return 0;
}

static int poll_data(struct sensors_poll_device_t *dev, sensors_event_t *data)
{
	struct input_event iev;
	struct sensor_context *ctx = (struct sensor_context *)dev;
	size_t res;
	float val;
	int i;

	/* Orientation sensor */
	for (;;) {
		res = read(ctx->accel_fd, &iev, sizeof(struct input_event));
		if (res != sizeof(struct input_event))
			break;

		if (iev.type != EV_ABS)
			continue;

		switch (iev.code) {
		case ABS_X:
			ctx->orientation_data[0] = iev.value;
			ctx->orientation_valid |= VALID_ROTATE_X;
			break;
		case ABS_Y:
			ctx->orientation_data[1] = iev.value;
			ctx->orientation_valid |= VALID_ROTATE_Y;
			break;
		case ABS_Z:
			ctx->orientation_data[2] = iev.value;
			ctx->orientation_valid |= VALID_ROTATE_Z;
			break;
		case ABS_MISC:
			ctx->light_data = iev.value;
			ctx->valid |= VALID_LIGHT;
			return 0;
		default:
			break;
		}

		if (ctx->orientation_valid == VALID_ROTATE_MASK) {
			ctx->valid |= VALID_ROTATE;
			return 0;
		}
	}

	return 0;
}

static int craft_fake_accel(struct sensors_poll_device_t *dev, sensors_event_t *data)
{
	struct sensor_context *ctx = (struct sensor_context *)dev;

	const double angle = 20.0;
	const double cos_angle = GRAVITY_EARTH * cos(angle / M_PI);
	const double sin_angle = GRAVITY_EARTH * sin(angle / M_PI);

	/*
	 *              y=max
	 *                |
	 *                |
	 *                |
	 *                |
	 * x=max ---------+--------- x=min
	 *                |
	 *                |
	 *                |
	 *                |
	 *              y=min
	 */

	if (ctx->orientation_data[0] >= 10000) {
		/* Landscape */
		data->acceleration.x = 0.0f;
		data->acceleration.y = cos_angle;
		data->acceleration.z = sin_angle;
	} else if (ctx->orientation_data[0] <= -10000) {
		/* Flipped landscape */
		data->acceleration.x = 0.0f;
		data->acceleration.y = -cos_angle;
		data->acceleration.z = -sin_angle;
	} else if (ctx->orientation_data[1] <= -10000) {
		/* Portrait */
		data->acceleration.x = cos_angle;
		data->acceleration.y = 0.0f;
		data->acceleration.z = sin_angle;
	} else if (ctx->orientation_data[1] >= 10000) {
		/* Flipped portrait */
		data->acceleration.x = -cos_angle;
		data->acceleration.y = 0.0f;
		data->acceleration.z = -sin_angle;
	} else {
		/* No change */
		return 0;
	}

	return 1;
}


static int submit_sensor(struct sensors_poll_device_t *dev, sensors_event_t *data)
{
	struct sensor_context *ctx = (struct sensor_context *)dev;
	int ret = 0;
	struct timespec t;
	const uint32_t end = ctx->sent;
	uint32_t start = end;
	ctx->sent = (ctx->sent + 1) % SENS_COUNT;

	/* Get time for the event. */
	memset(&t, 0, sizeof(t));
	clock_gettime(CLOCK_MONOTONIC, &t);
	data->timestamp = ((int64_t)(t.tv_sec) * 1000000000LL) + t.tv_nsec;
	data->version = sizeof(*data);

	do {
		start = (start + 1) % SENS_COUNT;

		switch (ctx->valid & (1 << start)) {
		case VALID_LIGHT:
			data->sensor = ID_AMBIENT_LIGHT;
			data->type = SENSOR_TYPE_LIGHT;

			data->light = ctx->light_data;

			ALOGD("%s[%i] LIGHT %d", __func__, __LINE__,
					ctx->light_data);

			ctx->valid &= ~(1 << start);
			return 1;
		case VALID_ROTATE:
			data->sensor = ID_ROTATION_VECTOR;
			data->type = SENSOR_TYPE_ROTATION_VECTOR;
			data->orientation.status = SENSOR_STATUS_ACCURACY_MEDIUM;

			float x = ctx->orientation_data[0];
			float y = ctx->orientation_data[1];
			float z = ctx->orientation_data[2];

			data->orientation.x = x;
			data->orientation.y = y;
			data->orientation.z = z;

			ALOGD("%s[%i] ROTATE %d %d %d -> %f %f %f",
				__func__, __LINE__,
				ctx->orientation_data[0],
				ctx->orientation_data[1],
				ctx->orientation_data[2],
				data->orientation.x,
				data->orientation.y,
				data->orientation.z);

			ctx->valid &= ~(1 << start);
			ctx->valid |= VALID_ACCEL;
			return 3;
		case VALID_ACCEL:
			data->sensor = ID_ACCELEROMETER;
			data->type = SENSOR_TYPE_ACCELEROMETER;
			data->acceleration.status = SENSOR_STATUS_ACCURACY_HIGH;

			ret = craft_fake_accel(dev, data);

			ALOGD("%s[%i] ACCEL %f %f %f",
				__func__, __LINE__,
				data->acceleration.x,
				data->acceleration.y,
				data->acceleration.z);

			ctx->valid &= ~(1 << start);
			return ret;
		}
	} while (start != end);

	return 0;
}

static int context__poll(struct sensors_poll_device_t *dev, sensors_event_t *data, int count)
{
	struct sensor_context *ctx = (struct sensor_context *)dev;
	int ret;

	while (1) {

		poll_data(dev, data);

		ret = submit_sensor(dev, data);
		if (ret)
			return ret;

		nanosleep(&ctx->delay, &ctx->delay);
	}

	return 0;
}

static const struct sensor_t sensor_list[] = {
	[0] = {
		.name		= "W500 Ambient Light sensor",
		.vendor		= "Unknown",
		.version	= 1,
		.handle		= ID_AMBIENT_LIGHT,
		.type		= SENSOR_TYPE_LIGHT,
		.maxRange	= 100,
		.resolution	= 1,
		.power		= 1,
		.minDelay	= 100,
	},
	[1] = {
		.name		= "W500 Gyro sensor",
		.vendor		= "BOSCH Sensortec",
		.version	= 1,
		.handle		= ID_ROTATION_VECTOR,
		.type		= SENSOR_TYPE_ROTATION_VECTOR,
		.maxRange	= 35000.0f,
		.resolution	= 1.0f,
		.power		= 1,
		.minDelay	= 100,
	},
	[2] = {
		.name		= "W500 Fake accelerometer",
		.vendor		= "FooBar Technology Group",
		.version	= 1,
		.handle		= ID_ACCELEROMETER,
		.type		= SENSOR_TYPE_ACCELEROMETER,
		.maxRange	= 2.8f,
		.resolution	= 1.0f/4032.0f,
		.power		= 3.0f,
		.minDelay	= 100,
	},
};

static int sensors__get_sensors_list(struct sensors_module_t *module,
					const struct sensor_t **list)
{
	*list = sensor_list;
	return sizeof(sensor_list)/sizeof(sensor_list[0]);
}


static int open_sensors(const struct hw_module_t *module, const char* id,
			struct hw_device_t **device)
{
	struct sensor_context *ctx;
	int fd;

	if (strcmp(id, SENSORS_HARDWARE_POLL))
		return -EINVAL;

	/* Allocate context */
	ctx = malloc(sizeof(*ctx));
	if (!ctx)
		return -ENOMEM;

	memset(ctx, 0, sizeof(*ctx));

	ctx->accel_fd = -1;
	ctx->delay.tv_sec = 0;
	ctx->delay.tv_nsec = 100;

	/* Do common setup */
	ctx->device.common.tag = HARDWARE_DEVICE_TAG;
	ctx->device.common.version = 0;
	ctx->device.common.module = (struct hw_module_t *)module;
	ctx->device.common.close = context__close;

	ctx->device.activate = context__activate;
	ctx->device.setDelay = context__setDelay;
	ctx->device.poll = context__poll;

	*device = &ctx->device.common;

	return 0;

err_light:
	close(ctx->accel_fd);
err_accel:
	free(ctx);
	return -ENOMEM;
}

static struct hw_module_methods_t sensors_module_methods = {
	.open	= open_sensors
};

struct sensors_module_t HAL_MODULE_INFO_SYM = {
	.common = {
		.tag		= HARDWARE_MODULE_TAG,
		.version_major	= 1,
		.version_minor	= 0,
		.id		= SENSORS_HARDWARE_MODULE_ID,
		.name		= "W500 SENSORS Module",
		.author		= "Marek Vasut",
		.methods	= &sensors_module_methods,
	},
	.get_sensors_list	= sensors__get_sensors_list,
};
