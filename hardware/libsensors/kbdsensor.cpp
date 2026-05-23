/**
 *
 * Atkbd style sensor
 *
 * Copyright (C) 2011-2013 The Android-x86 Open Source Project
 *
 * by Chih-Wei Huang <cwhuang@linux.org.tw>
 *
 * Licensed under GPLv2 or later
 *
 **/

#define LOG_TAG "KbdSensor"

#include <cmath>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <sys/stat.h>
#include <poll.h>
#include <fcntl.h>
#include <dirent.h>
#include <cutils/log.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <hardware/sensors.h>
#include <cutils/properties.h>

struct KbdSensorKeys {
	char name[64];
	int keys[8];
} KeysType[] = {
	{ "", { } },
	{ "AT Translated Set 2 keyboard", { EV_KEY, KEY_UP, KEY_RIGHT, KEY_DOWN, KEY_LEFT, KEY_LEFTALT, KEY_LEFTCTRL, 1 } },
	{ "AT Translated Set 2 keyboard", { EV_MSC, 91, 115, 123, 109, KEY_LEFTALT, KEY_LEFTCTRL, 3 } },
	{ "AT Translated Set 2 keyboard", { EV_KEY, KEY_F5, KEY_F8, KEY_F6, KEY_F7, KEY_LEFTALT, KEY_LEFTCTRL, 1 } },
	{ "AT Translated Set 2 keyboard", { EV_KEY, KEY_F9, KEY_F12, KEY_F10, KEY_F11, KEY_LEFTALT, KEY_LEFTCTRL, 1 } },
	{ "Asus Laptop extra buttons", { EV_KEY, KEY_F9, KEY_F12, KEY_F10, KEY_F11, KEY_LEFTALT, KEY_LEFTCTRL, 2 } },
	{ "HP WMI hotkeys", { -1, KEY_DIRECTION, 0, 0, 0, 0, 0, 3 } },
};

const int ID_ACCELERATION = (SENSORS_HANDLE_BASE + 0);

template <typename T> struct SensorFd : T {
	SensorFd(const struct hw_module_t *module, struct hw_device_t **device);
};

template <typename T> SensorFd<T>::SensorFd(const struct hw_module_t *module, struct hw_device_t **device)
{
	this->common.tag     = HARDWARE_DEVICE_TAG;
	this->common.version = 0;
	this->common.module  = const_cast<struct hw_module_t *>(module);
	*device              = &this->common;
	ALOGD("%s: module=%p dev=%p", __FUNCTION__, module, *device);
}

struct SensorPollContext : SensorFd<sensors_poll_device_t> {
  public:
	SensorPollContext(const struct hw_module_t *module, struct hw_device_t **device);
	~SensorPollContext();
	bool isValid() const { return (pfd.fd >= 0); }

  private:
	static int poll_close(struct hw_device_t *dev);
	static int poll_activate(struct sensors_poll_device_t *dev, int handle, int enabled);
	static int poll_setDelay(struct sensors_poll_device_t *dev, int handle, int64_t ns);
	static int poll_poll(struct sensors_poll_device_t *dev, sensors_event_t *data, int count);

	int doPoll(sensors_event_t *data, int count);

	enum {
		ROT_0,
		ROT_90,
		ROT_180,
		ROT_270
	};

	bool enabled;
	int rotation;
	struct timespec delay;
	struct pollfd pfd;
	sensors_event_t orients[4];
	KbdSensorKeys *ktype;
};

SensorPollContext::SensorPollContext(const struct hw_module_t *module, struct hw_device_t **device)
      : SensorFd<sensors_poll_device_t>(module, device), enabled(false), rotation(ROT_0), ktype(KeysType)
{
	common.close = poll_close;
	activate     = poll_activate;
	setDelay     = poll_setDelay;
	poll         = poll_poll;

	int &fd = pfd.fd;
	fd = -1;
	const char *dirname = "/dev/input";
	char prop[PROPERTY_VALUE_MAX];
	if (property_get("hal.sensors.kbd.keys", prop, 0))
		sscanf(prop, "%s,%d,%d,%d,%d,%d,%d,%d,%d", ktype->name, ktype->keys,
				ktype->keys + 1, ktype->keys + 2, ktype->keys + 3, ktype->keys + 4, ktype->keys + 5, ktype->keys + 6, ktype->keys + 7);
	else if (property_get("hal.sensors.kbd.type", prop, 0))
		ktype = &KeysType[atoi(prop)];
	else
		ktype = 0;
	if (DIR *dir = opendir(dirname)) {
		char name[PATH_MAX];
		while (struct dirent *de = readdir(dir)) {
			if (de->d_name[0] != 'e') // not eventX
				continue;
			snprintf(name, PATH_MAX, "%s/%s", dirname, de->d_name);
			fd = open(name, O_RDWR);
			if (fd < 0) {
				ALOGE("could not open %s, %s", name, strerror(errno));
				continue;
			}
			name[sizeof(name) - 1] = '\0';
			if (ioctl(fd, EVIOCGNAME(sizeof(name) - 1), &name) < 1) {
				ALOGE("could not get device name for %s, %s\n", name, strerror(errno));
				name[0] = '\0';
			}

			if (ktype) {
				if (!strcmp(name, ktype->name))
					break;
			} else {
				ktype = KeysType + (sizeof(KeysType) / sizeof(KeysType[0]));
				while (--ktype != KeysType)
					if (!strcmp(name, ktype->name))
						break;
				if (ktype != KeysType)
					break;
				else
					ktype = 0;
			}
			close(fd);
			fd = -1;
		}
		ALOGI_IF(fd >= 0, "Open %s ok, fd=%d", name, fd);
		closedir(dir);
	}

	pfd.events = POLLIN;
	orients[ROT_0].version = sizeof(sensors_event_t);
	orients[ROT_0].sensor = ID_ACCELERATION;
	orients[ROT_0].type = SENSOR_TYPE_ACCELEROMETER;
	orients[ROT_0].acceleration.status = SENSOR_STATUS_ACCURACY_HIGH;
	orients[ROT_270] = orients[ROT_180] = orients[ROT_90] = orients[ROT_0];
	const double angle = 20.0;
	const double cos_angle = GRAVITY_EARTH * cos(angle / M_PI);
	const double sin_angle = GRAVITY_EARTH * sin(angle / M_PI);
	orients[ROT_0].acceleration.x   = 0.0;
	orients[ROT_0].acceleration.y   = cos_angle;
	orients[ROT_0].acceleration.z   = sin_angle;
	orients[ROT_90].acceleration.x  = cos_angle;
	orients[ROT_90].acceleration.y  = 0.0;
	orients[ROT_90].acceleration.z  = sin_angle;
	orients[ROT_180].acceleration.x = 0.0;
	orients[ROT_180].acceleration.y = -cos_angle;
	orients[ROT_180].acceleration.z = -sin_angle;
	orients[ROT_270].acceleration.x = -cos_angle;
	orients[ROT_270].acceleration.y = 0.0;
	orients[ROT_270].acceleration.z = -sin_angle;

	delay.tv_sec = 0;
	delay.tv_nsec = 200000000L;

	ALOGD("%s: dev=%p fd=%d", __FUNCTION__, this, fd);
}

SensorPollContext::~SensorPollContext()
{
	close(pfd.fd);
}

int SensorPollContext::poll_close(struct hw_device_t *dev)
{
	ALOGD("%s: dev=%p", __FUNCTION__, dev);
	delete reinterpret_cast<SensorPollContext *>(dev);
	return 0;
}

int SensorPollContext::poll_activate(struct sensors_poll_device_t *dev, int handle, int enabled)
{
	ALOGD("%s: dev=%p handle=%d enabled=%d", __FUNCTION__, dev, handle, enabled);
	SensorPollContext *ctx = reinterpret_cast<SensorPollContext *>(dev);
	ctx->enabled = enabled;
	return 0;
}

int SensorPollContext::poll_setDelay(struct sensors_poll_device_t *dev, int handle, int64_t ns)
{
	ALOGD("%s: dev=%p delay-ns=%lld", __FUNCTION__, dev, ns);
	return 0;
}

int SensorPollContext::poll_poll(struct sensors_poll_device_t *dev, sensors_event_t *data, int count)
{
	ALOGV("%s: dev=%p data=%p count=%d", __FUNCTION__, dev, data, count);
	SensorPollContext *ctx = reinterpret_cast<SensorPollContext *>(dev);
	return ctx->doPoll(data, count);
}

int SensorPollContext::doPoll(sensors_event_t *data, int count)
{
	nanosleep(&delay, 0);
	if (!isValid())
		return 0;

	int *keys = ktype->keys;
	while (int pollres = ::poll(&pfd, 1, -1)) {
		if (pollres < 0) {
			ALOGE("%s: poll %d error: %s", __FUNCTION__, pfd.fd, strerror(errno));
			break;
		}
		if (!(pfd.revents & POLLIN)) {
			ALOGW("%s: ignore revents %d", __FUNCTION__, pfd.revents);
			continue;
		}

		struct input_event iev;
		size_t res = ::read(pfd.fd, &iev, sizeof(iev));
		if (res < sizeof(iev)) {
			ALOGW("insufficient input data(%d)? fd=%d", res, pfd.fd);
			continue;
		}
		ALOGV("type=%d scancode=%d value=%d from fd=%d", iev.type, iev.code, iev.value, pfd.fd);
		if (iev.type == keys[0]) {
			int rot;
			int input = (keys[0] == EV_MSC) ? iev.value : iev.code;
			if (input == keys[1])
				rot = ROT_0;
			else if (input == keys[2])
				rot = ROT_90;
			else if (input == keys[3])
				rot = ROT_180;
			else if (input == keys[4])
				rot = ROT_270;
			else if (input == keys[5] || input == keys[6])
				rot = rotation;
			else
				rot = -1;

			if (rot >= 0) {
				if (rot != rotation) {
					ALOGI("orientation changed from %d to %d", rotation * 90, rot * 90);
					rotation = rot;
				}
				if (enabled && count > 0)
					break;
			}
		} else if (iev.type == EV_KEY) {
			if (iev.code == keys[1] && iev.value) {
				if (rotation == ROT_270)
					rotation = ROT_0;
				else
					rotation++;
			}
			if (iev.code == keys[2] && iev.value) {
				if (rotation == ROT_0)
					rotation = ROT_270;
				else
					rotation--;
			}
			break;
		} else if (iev.type == EV_SW && iev.code == SW_TABLET_MODE) {
			if (!iev.value)
				rotation = ROT_0;
			else if (rotation == ROT_0)
				rotation = ROT_90;
			break;
		}
	}

	int cnt;
	struct timespec t;
	data[0] = orients[rotation];
	t.tv_sec = t.tv_nsec = 0;
	clock_gettime(CLOCK_MONOTONIC, &t);
	data[0].timestamp = int64_t(t.tv_sec) * 1000000000LL + t.tv_nsec;
	for (cnt = 1; cnt < keys[7] && cnt < count; ++cnt) {
		data[cnt] = data[cnt - 1];
		data[cnt].timestamp += delay.tv_nsec;
		nanosleep(&delay, 0);
	}
	ALOGV("%s: dev=%p fd=%d rotation=%d cnt=%d", __FUNCTION__, this, pfd.fd, rotation * 90, cnt);
	return cnt;
}

static int open_kbd_sensor(const struct hw_module_t *module, const char *id, struct hw_device_t **device)
{
	ALOGD("%s: id=%s", __FUNCTION__, id);
	SensorPollContext *ctx = new SensorPollContext(module, device);
	return (ctx && ctx->isValid()) ? 0 : -EINVAL;
}

static struct sensor_t sSensorListInit[] = {
	{
		name: "Kbd Orientation Sensor",
		vendor: "Android-x86 Open Source Project",
		version: 1,
		handle: ID_ACCELERATION,
		type: SENSOR_TYPE_ACCELEROMETER,
		maxRange: 2.8f,
		resolution: 1.0f/4032.0f,
		power: 3.0f,
		minDelay: 0,
		fifoReservedEventCount: 0,
		fifoMaxEventCount: 0,
		reserved: { }
	}
};

static int sensors_get_sensors_list(struct sensors_module_t *module, struct sensor_t const **list)
{
	*list = sSensorListInit;
	return sizeof(sSensorListInit) / sizeof(struct sensor_t);
}

static struct hw_module_methods_t sensors_methods = {
	open: open_kbd_sensor
};

struct sensors_module_t HAL_MODULE_INFO_SYM = {
	common: {
		tag: HARDWARE_MODULE_TAG,
		version_major: 2,
		version_minor: 3,
		id: SENSORS_HARDWARE_MODULE_ID,
		name: "Kbd Orientation Sensor",
		author: "Chih-Wei Huang",
		methods: &sensors_methods,
		dso: 0,
		reserved: { }
	},
	get_sensors_list: sensors_get_sensors_list
};
