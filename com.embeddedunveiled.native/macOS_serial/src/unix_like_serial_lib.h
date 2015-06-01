/***************************************************************************************************
 * Author : Rishi Gupta
 *
 * This file is part of 'serial communication manager' library.
 *
 * The 'serial communication manager' is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * The 'serial communication manager' is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with serial communication manager. If not, see <http://www.gnu.org/licenses/>.
 *
 ***************************************************************************************************/

#if defined (__linux__) || defined (__APPLE__) || defined (__SunOS)

#if defined (__APPLE__)
#include <IOKit/IOMessage.h>
#endif


#ifndef UNIX_LIKE_SERIAL_LIB_H_
#define UNIX_LIKE_SERIAL_LIB_H_

#include <pthread.h>		/* POSIX thread definitions	      */

	/* Structure representing data that is passed to each data looper thread with info corresponding to that file descriptor. */
	struct com_thread_params {
		JavaVM *jvm;
		int fd;
		jobject looper;
		pthread_t data_thread_id;
		pthread_t event_thread_id;
		int evfd;               /* used to get epoll_wait and kevent out of waiting state so that it checks for thread exit condition. */
		int data_thread_exit;   /* set to 1 to indicate that the data thread should exit gracefully. */
		int event_thread_exit;  /* set to 1 to indicate that the event thread should exit gracefully. */
		pthread_mutex_t *mutex; /* protect global shared data from synchronous access */
		int data_init_done;     /* indicates data thread has been successfully initialized or not; 0 is default 1 is success, otherwise error number as set by thread */
		int event_init_done;    /* indicates event thread has been successfully initialized or not; 0 is default 1 is success, otherwise error number as set by thread */
		pthread_attr_t data_thread_attr;
		pthread_attr_t event_thread_attr;
	};

#if defined (__linux__)
	struct port_info {
		JavaVM *jvm;
		const char *port_name;
		int fd;
		int thread_exit;
		jobject port_listener;
		pthread_t thread_id;
		pthread_mutex_t *mutex;
	};
#elif defined (__APPLE__)
	struct port_info {
		JavaVM *jvm;
		JNIEnv* env;
		const char *port_name;
		int fd;
		int thread_exit;
		jobject port_listener;
		jmethodID mid;
		pthread_t thread_id;
		pthread_mutex_t *mutex;
		struct port_info *data;
		IONotificationPortRef notification_port;
		int tempVal;
	};

	/* Structure to hold reference to driver and subscribed notification. */
	struct driver_ref {
		io_service_t service;
		io_object_t notification;
		struct port_info *data;
	};

#elif defined (__SunOS)
#endif

/* function prototypes */
extern void LOGE(JNIEnv *env);
extern void *data_looper(void *params);
extern void *event_looper(void *params);
extern void *port_monitor(void *params);
extern int serial_delay(unsigned usecs);

#endif /* UNIX_LIKE_SERIAL_LIB_H_ */

#endif /* end compiling for Unix-like OS */

