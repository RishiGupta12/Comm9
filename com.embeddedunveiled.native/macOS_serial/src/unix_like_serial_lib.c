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

/* We have tried to follow the philosophy that resources specific to thread should be held by thread
 * and that the thread is responsible for cleaning them before exiting. As appropriate a policy is
 * followed that if listener exist and port is removed, CPU usage does not go very high. */

#if defined (__linux__) || defined (__APPLE__) || defined (__SunOS)

#include <unistd.h>     	/* UNIX standard function definitions */
#include <stdio.h>
#include <stdlib.h>     	/* Standard ANSI routines             */
#include <string.h>     	/* String function definitions        */
#include <fcntl.h>      	/* File control definitions           */
#include <errno.h>      	/* Error number definitions           */
#include <dirent.h>     	/* Format of directory entries        */
#include <sys/types.h>  	/* Primitive System Data Types        */
#include <sys/stat.h>   	/* Defines the structure of the data  */
#include <pthread.h>		/* POSIX thread definitions	          */
#include <sys/select.h>

#if defined (__linux__)
#include <linux/types.h>
#include <linux/termios.h>  /* POSIX terminal control definitions for Linux (termios2) */
#include <linux/serial.h>
#include <linux/ioctl.h>
#include <sys/eventfd.h>    /* Linux eventfd for event notification. */
#include <sys/epoll.h>      /* epoll feature of Linux	              */
#include <signal.h>
#include <libudev.h>
#include <locale.h>
#endif

#if defined (__APPLE__)
#include <termios.h>
#include <paths.h>
#include <sys/ioctl.h>
#include <sysexits.h>
#include <sys/param.h>
#include <sys/event.h>
#include <CoreFoundation/CoreFoundation.h>
#include <IOKit/IOKitLib.h>
#include <IOKit/serial/IOSerialKeys.h>
#include <IOKit/serial/ioss.h>
#include <IOKit/IOBSD.h>
#include <IOKit/IOMessage.h>
#include <IOKit/usb/IOUSBLib.h>
#endif

#if defined (__SunOS)
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/filio.h>
#endif

#include <jni.h>
#include "unix_like_serial_lib.h"

#define DBG 1

JavaVM *jvm_event;
JavaVM *jvm_port;

#if defined (__APPLE__)
/* Holds information for port monitor facility. */
int pm_index = 0;
struct driver_ref* pm_info[2048] = {0};
#endif

/* Do not let any exception propagate. Handle and clear it. */
void LOGE(JNIEnv *env) {
	(*env)->ExceptionDescribe(env);
	(*env)->ExceptionClear(env);
}

/* pselect() is used to provide delay whenever required. */
int serial_delay(unsigned milliSeconds) {
	struct timespec t;
	t.tv_sec  = milliSeconds/1000;
	t.tv_nsec = 0;
	pselect(1, 0, 0, 0, &t, 0);
	return 0;
}

/* This thread wait for data to be available on fd and enqueues it in data queue managed by java layer.
 * For unrecoverable errors thread would like to exit and try again. */
void *data_looper(void *arg) {
	int i = -1;
	int negative = -1;
	int index = 0;
	int partialData = -1;
	int errorCount = 0;
	ssize_t ret = -1;
	jbyte buffer[1024];
	jbyte final_buf[1024 * 3]; 	  /* Sufficient enough to deal with consecutive multiple partial reads. */
	jbyteArray dataRead;
	int data_available = 0;

#if defined (__linux__)
	/* Epoll is used for Linux systems.
	 * ev_port refers to serial port fd and ev_exit refers to fd on which we make an event happen explicitly
	 * so as to signal epoll_wait come out of waiting state. */
	int MAXEVENTS = 4;
	int epfd = 0;
	int evfd = 0;
	struct epoll_event ev_port;
	struct epoll_event ev_exit;
	struct epoll_event event;
	struct epoll_event *events = calloc(MAXEVENTS, sizeof(event));
#endif
#if defined (__APPLE__)
	/* Kqueue is used for MAC OS X systems. */
	int kq;
	struct kevent chlist[2];   /* events to monitor */
	struct kevent evlist[2];   /* events that were triggered */
	int pipe1[2]; /* pipe1[0] is reading end, and pipe1[1] is writing end. */
#endif

	pthread_mutex_lock(((struct com_thread_params*) arg)->mutex);

	struct com_thread_params* params = (struct com_thread_params*) arg;
	JavaVM *jvm = (*params).jvm;
	int fd = (*params).fd;
	jobject looper = (*params).looper;

	/* The JNIEnv is valid only in the current thread. So, threads created in C should attach itself to the VM
	 * and obtain a JNI interface pointer. */
	void* env1;
	JNIEnv* env;
	if((*jvm)->AttachCurrentThread(jvm, &env1, NULL) != JNI_OK) {
		if(DBG) fprintf(stderr, "%s \n", "NATIVE data_looper() thread failed to attach itself to JVM to access JNI ENV. Please RETRY registering data listener !");
		if(DBG) fflush(stderr);
		((struct com_thread_params*) arg)->data_init_done = -240;
		pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
		pthread_exit((void *)0);
	}

	env = (JNIEnv*) env1;
	/* Local references are valid for the duration of a native method call.
	   They are freed automatically after the native method returns. */
	jclass SerialComLooper = (*env)->GetObjectClass(env, looper);
	if(SerialComLooper == NULL) {
		if(DBG) fprintf(stderr, "%s \n", "NATIVE data_looper() thread could not get class of object of type looper. Please RETRY registering data listener !");
		if(DBG) fflush(stderr);
		((struct com_thread_params*) arg)->data_init_done = -240;
		(*jvm)->DetachCurrentThread(jvm);
		pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
		pthread_exit((void *)0);
	}

	jmethodID mid = (*env)->GetMethodID(env, SerialComLooper, "insertInDataQueue", "([B)V");
	if((*env)->ExceptionOccurred(env)) {
		LOGE(env);
	}
	if(mid == NULL) {
		if(DBG) fprintf(stderr, "%s \n", "NATIVE data_looper() thread failed to retrieve method id of method insertInDataQueue in class SerialComLooper. Please RETRY registering data listener !");
		if(DBG) fflush(stderr);
		((struct com_thread_params*) arg)->data_init_done = -240;
		(*jvm)->DetachCurrentThread(jvm);
		pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
		pthread_exit((void *)0);
	}

	jmethodID mide = (*env)->GetMethodID(env, SerialComLooper, "insertInDataErrorQueue", "(I)V");
	if((*env)->ExceptionOccurred(env)) {
		LOGE(env);
	}
	if(mide == NULL) {
		if(DBG) fprintf(stderr, "%s \n", "NATIVE data_looper() thread failed to retrieve method id of method insertInDataErrorQueue in class SerialComLooper. Please RETRY registering data listener !");
		if(DBG) fflush(stderr);
		((struct com_thread_params*) arg)->data_init_done = -240;
		(*jvm)->DetachCurrentThread(jvm);
		pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
		pthread_exit((void *)0);
	}

#if defined (__linux__)
	errno = 0;
	ret  = eventfd(0, EFD_NONBLOCK);
	if(ret < 0) {
		if(DBG) fprintf(stderr, "%s %d\n", "NATIVE data_looper() thread failed to create eventfd with error number : -", errno);
		if(DBG) fprintf(stderr, "%s \n", "NATIVE data_looper() thread exiting. Please RETRY registering data listener !");
		if(DBG) fflush(stderr);
		((struct com_thread_params*) arg)->data_init_done = negative * errno;
		(*jvm)->DetachCurrentThread(jvm);
		pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
		pthread_exit((void *)0);
	}
	((struct com_thread_params*) arg)->evfd = ret;  /* Save evfd for cleanup. */
	evfd = ((struct com_thread_params*) arg)->evfd;

	errno = 0;
	epfd = epoll_create(2);
	if(epfd < 0) {
		if(DBG) fprintf(stderr, "%s%d\n", "NATIVE data_looper() thread failed in epoll_create() with error number : -", errno);
		if(DBG) fprintf(stderr, "%s \n", "NATIVE data_looper() thread exiting. Please RETRY registering data listener !");
		if(DBG) fflush(stderr);
		close(((struct com_thread_params*) arg)->evfd);
		((struct com_thread_params*) arg)->data_init_done = negative * errno;
		(*jvm)->DetachCurrentThread(jvm);
		pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
		pthread_exit((void *)0);
	}

	/* add serial port to epoll wait mechanism. Use level triggered (returned immediately if there is data in read buffer)
	 * epoll mechanism.  */
	ev_port.events = (EPOLLIN | EPOLLPRI | EPOLLERR | EPOLLHUP);
	ev_port.data.fd = fd;
	errno = 0;
	ret = epoll_ctl(epfd, EPOLL_CTL_ADD, fd, &ev_port);
	if(ret < 0) {
		if(DBG) fprintf(stderr, "%s%d\n", "NATIVE data_looper() thread failed in epoll_ctl() for adding serial port with error number : -", errno);
		if(DBG) fprintf(stderr, "%s \n", "NATIVE data_looper() thread exiting. Please RETRY registering data listener !");
		if(DBG) fflush(stderr);
		close(epfd);
		close(((struct com_thread_params*) arg)->evfd);
		((struct com_thread_params*) arg)->data_init_done = negative * errno;
		(*jvm)->DetachCurrentThread(jvm);
		pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
		pthread_exit((void *)0);
	}

	/* add our thread exit signal fd to epoll wait mechanism. */
	ev_exit.events = (EPOLLIN | EPOLLPRI | EPOLLERR | EPOLLHUP);
	ev_exit.data.fd = ((struct com_thread_params*) arg)->evfd;
	errno = 0;
	ret = epoll_ctl(epfd, EPOLL_CTL_ADD, ((struct com_thread_params*) arg)->evfd, &ev_exit);
	if(ret < 0) {
		if(DBG) fprintf(stderr, "%s%d\n", "NATIVE data_looper() thread failed in epoll_ctl() for adding exit event evfd with error number : -", errno);
		if(DBG) fprintf(stderr, "%s \n", "NATIVE data_looper() thread exiting. Please RETRY registering data listener !");
		if(DBG) fflush(stderr);
		close(epfd);
		close(((struct com_thread_params*) arg)->evfd);
		((struct com_thread_params*) arg)->data_init_done = negative * errno;
		(*jvm)->DetachCurrentThread(jvm);
		pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
		pthread_exit((void *)0);
	}
#endif
#if defined (__APPLE__)
	errno = 0;
	ret = pipe(pipe1);
	if(ret < 0) {
		if(DBG) fprintf(stderr, "%s%d\n", "NATIVE data_looper() thread failed in pipe() with error number : -", errno);
		if(DBG) fprintf(stderr, "%s \n", "NATIVE data_looper() thread exiting. Please RETRY registering data listener !");
		if(DBG) fflush(stderr);
		((struct com_thread_params*) arg)->data_init_done = negative * errno;
		(*jvm)->DetachCurrentThread(jvm);
		pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
		pthread_exit((void *)0);
	}
	((struct com_thread_params*) arg)->evfd = pipe1[1];  /* Save writing end of pipe for exit and cleanup. */

	/* The kqueue() system call creates a new kernel event queue and returns a file descriptor. */
	errno = 0;
	kq = kqueue();
	if(kq < 0) {
		if(DBG) fprintf(stderr, "%s%d\n", "NATIVE data_looper() thread failed in kevent() for adding serial port with error number : -", errno);
		if(DBG) fprintf(stderr, "%s \n", "NATIVE data_looper() thread exiting. Please RETRY registering data listener !");
		if(DBG) fflush(stderr);
		((struct com_thread_params*) arg)->data_init_done = negative * errno;
		close(pipe1[0]);
		close(pipe1[1]);
		(*jvm)->DetachCurrentThread(jvm);
		pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
		pthread_exit((void *)0);
	}

	/* Initialize what changes to be monitor on which fd. */
	EV_SET(&chlist[0], fd, EVFILT_READ, EV_ADD , 0, 0, NULL);
	EV_SET(&chlist[1], pipe1[0], EVFILT_READ, EV_ADD , 0, 0, NULL);
#endif

	/* indicate success to caller so it can return success to java layer */
	((struct com_thread_params*) arg)->data_init_done = 1;
	pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);

	/* This keep looping until listener is unregistered, waiting for data and passing it to java layer. */
	while(1) {

#if defined (__linux__)
		errno = 0;
		ret = epoll_wait(epfd, events, MAXEVENTS, -1);
		if(ret <= 0) {
			/* ret < 0 if error occurs, ret = 0 if no fd available for read.
			 * for error (unlikely to happen) just restart looping. */
			continue;
		}
#endif
#if defined (__APPLE__)
		errno = 0;
		ret = kevent(kq, chlist, 2, evlist, 2, NULL);
		if(ret <= 0) {
			/* for error (unlikely to happen) just restart looping. */
			continue;
		}
#endif
#if defined (__linux__)
		if((events[0].data.fd == evfd) || (events[1].data.fd == evfd)) {
			/* check if thread should exit due to un-registration of listener. */
			if(1 == ((struct com_thread_params*) arg)->data_thread_exit) {
				close(epfd);
				close(((struct com_thread_params*) arg)->evfd);
				ret = (*jvm)->DetachCurrentThread(jvm);
				if(ret != JNI_OK) {
					if(DBG) fprintf(stderr, "%s %d\n", "NATIVE data_looper() failed to exit data monitor thread with JNI error ", (int)ret);
					if(DBG) fflush(stderr);
				}
				pthread_exit((void *)0);
			}
		}
#endif
#if defined (__APPLE__)
		/* Depending upon how many events has happened, pipe1[0] fd can be at 1st or 2nd
		 * index in evlist array. */
		if((evlist[0].ident == pipe1[0]) || (evlist[1].ident == pipe1[0])) {
			/* check if thread should exit due to un-registration of listener. */
			if(1 == ((struct com_thread_params*) arg)->data_thread_exit) {
				close(kq);
				close(pipe1[0]);
				close(pipe1[1]);
				ret = (*jvm)->DetachCurrentThread(jvm);
				if(ret != JNI_OK) {
					if(DBG) fprintf(stderr, "%s %d\n", "NATIVE data_looper() failed to exit data monitor thread with JNI error ", (ssize_t)ret);
					if(DBG) fflush(stderr);
				}
				pthread_exit((void *)0);
			}
		}
#endif

#if defined (__linux__)
		if((events[0].events & EPOLLIN) && !(events[0].events & EPOLLERR)) {
#endif
#if defined (__APPLE__)
			if((evlist[0].ident == fd) && !(evlist[0].flags & EV_ERROR)) {
#endif
				/* input event happened, no error occurred, we have data to read on file descriptor. */
				do {

					data_available = 0;
					errno = 0;
					ret = read(fd, buffer, sizeof(buffer));
					if(ret > 0 && errno == 0) {
						/* This indicates we got success and have read data. */
						/* If there is partial data read previously, append this data. */
						if(partialData == 1) {
							for(i = index; i < ret; i++) {
								final_buf[i] = buffer[i];
							}
							dataRead = (*env)->NewByteArray(env, index + ret);
							(*env)->SetByteArrayRegion(env, dataRead, 0, index + ret, final_buf);
							data_available = 1;
							break;
						}else {
							/* Pass the successful read to java layer straight away. */
							dataRead = (*env)->NewByteArray(env, ret);
							(*env)->SetByteArrayRegion(env, dataRead, 0, ret, buffer);
							data_available = 1;
							break;
						}
					}else if(ret > 0 && errno == EINTR) {
						/* This indicates, there is data to read, however, we got interrupted before we finish reading
						 * all of the available data. So we need to save this partial data and get back to read remaining. */
						for(i = index; i < ret; i++) {
							final_buf[i] = buffer[i];
						}
						index = ret;
						partialData = 1;
						continue;
					}else if(ret < 0) {
						if(errno == EINTR) {
							/* This indicates that we should retry as we are just interrupted by a signal. */
							continue;
						}else {
							/* This indicates, there was data to read but we got an error during read operation, notify application. */
#if defined (__linux__)
							(*env)->CallVoidMethod(env, looper, mide, errno);
							if((*env)->ExceptionOccurred(env)) {
								LOGE(env);
							}
#endif
#if defined (__APPLE__)
							(*env)->CallVoidMethod(env, looper, mide, errno);
							if((*env)->ExceptionOccurred(env)) {
								LOGE(env);
							}
#endif
#if defined (__SunOS)
//TODO solaris
#endif
							break;
						}
					}else if(ret == 0) {
						/* Not possible because fd has data as indicated by epoll/kevent. */
						break;
					}else {
					}
				} while(1);

				if(data_available == 1) {
					/* once we have successfully read the data, let us pass this to java layer. */
					(*env)->CallVoidMethod(env, looper, mid, dataRead);
					if((*env)->ExceptionOccurred(env)) {
						LOGE(env);
					}
				}

			}else {
#if defined (__linux__)
				if(events[0].events & (EPOLLERR|EPOLLHUP)) {
					errorCount++;
					/* minimize JNI transition by setting threshold for when application will be called. */
					if(errorCount == 100) {
						(*env)->CallVoidMethod(env, looper, mide, events[0].events);
						if((*env)->ExceptionOccurred(env)) {
							LOGE(env);
						}
						errorCount = 0; // reset error count
					}
				}
#endif
#if defined (__APPLE__)
				if(evlist[0].flags & EV_ERROR) {
					errorCount++;
					/* minimize JNI transition by setting threshold for when application will be called. */
					if(errorCount == 100) {
						(*env)->CallVoidMethod(env, looper, mide, evlist[0].data);
						if((*env)->ExceptionOccurred(env)) {
							LOGE(env);
						}
						errorCount = 0; // reset error count
					}
				}
#endif
#if defined (__SunOS)
//TODO solaris
#endif
			}
		} /* Go back to loop (while loop) again waiting for the data, available to read. */

		return ((void *)0);
	}

	/* This handler is invoked whenever application unregisters event listener. */
	void event_exit_signal_handler(int signal_number) {
		int ret = -1;
		if(signal_number == SIGUSR1) {
			ret = (*jvm_port)->DetachCurrentThread(jvm_port);
			if(ret != JNI_OK) {
				if(DBG) fprintf(stderr, "%s %d\n", "NATIVE exit_signal_handler() failed to exit event thread with JNI error ", ret);
				if(DBG) fflush(stderr);
			}
			pthread_exit((void *)0);
		}
	}

	/* This thread wait for a serial event to occur and enqueues it in event queue managed by java layer. */
	/* TIOCMWAIT RETURNS -EIO IF DEVICE FROM USB PORT HAS BEEN REMOVED */
	void *event_looper(void *arg) {
		int ret = 0;
		struct com_thread_params* params = (struct com_thread_params*) arg;
		JavaVM *jvm = (*params).jvm;
		jvm_event = (*params).jvm;
		int fd = (*params).fd;
		jobject looper = (*params).looper;

		int CTS =  0x01;  // 0000001
		int DSR =  0x02;  // 0000010
		int DCD =  0x04;  // 0000100
		int RI  =  0x08;  // 0001000
		int lines_status = 0;
		int cts,dsr,dcd,ri = 0;
		int event = 0;
#if defined (__APPLE__)
		int oldstate = 0;
		int newstate = 0;
#endif

		pthread_mutex_lock(((struct com_thread_params*) arg)->mutex);

		/* The JNIEnv is valid only in the current thread. So, threads created should attach itself to the VM and obtain a JNI interface pointer. */
		void* env1;
		JNIEnv* env;
		if((*jvm)->AttachCurrentThread(jvm, &env1, NULL) != JNI_OK) {
			if(DBG) fprintf(stderr, "%s \n", "NATIVE event_looper() thread failed to attach itself to JVM. RETRY registering event listener !");
			if(DBG) fflush(stderr);
			((struct com_thread_params*) arg)->event_init_done = -240;
			pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
			pthread_exit((void *)0);
		}
		env = (JNIEnv*) env1;

		jclass SerialComLooper = (*env)->GetObjectClass(env, looper);
		if(SerialComLooper == NULL) {
			if(DBG) fprintf(stderr, "%s \n", "NATIVE event_looper() thread could not get class of object of type looper. RETRY registering event listener !");
			if(DBG) fflush(stderr);
			((struct com_thread_params*) arg)->event_init_done = -240;
			(*jvm)->DetachCurrentThread(jvm);
			pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
			pthread_exit((void *)0);
		}

		jmethodID mid = (*env)->GetMethodID(env, SerialComLooper, "insertInEventQueue", "(I)V");
		if((*env)->ExceptionOccurred(env)) {
			LOGE(env);
		}
		if(mid == NULL) {
			if(DBG) fprintf(stderr, "%s \n", "NATIVE data_looper() thread failed to retrieve method id of method insertInDataQueue in class SerialComLooper. RETRY registering data listener !");
			if(DBG) fflush(stderr);
			((struct com_thread_params*) arg)->event_init_done = -240;
			(*jvm)->DetachCurrentThread(jvm);
			pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
			pthread_exit((void *)0);
		}

		/* Install signal handler that will be invoked to indicate that the thread should exit. */
		if(signal(SIGUSR1, event_exit_signal_handler) == SIG_ERR) {
			if(DBG) fprintf(stderr, "%s\n", "Unable to create handler for SIGUSR1. RETRY registering data listener !");
			if(DBG) fflush(stderr);
			((struct com_thread_params*) arg)->event_init_done = -240;
			(*jvm)->DetachCurrentThread(jvm);
			pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);
			pthread_exit((void *)0);
		}

		/* indicate success to caller so it can return success to java layer */
		((struct com_thread_params*) arg)->event_init_done = 1;
		pthread_mutex_unlock(((struct com_thread_params*) arg)->mutex);

		/* This keep looping until listener is unregistered, waiting for events and passing it to java layer.
		 * This sleep within the kernel until something happens to the MSR register of the tty device. */
		while(1) {
			lines_status = 0;
			cts = 0;
			dsr = 0;
			dcd = 0;
			ri = 0;
			event = 0;

#if defined (__linux__)
			/* When the user removes port on which this thread was calling this ioctl, this thread keep giving
			 * error -5 and keep looping in this ioctl for Linux. */
			errno = 0;
			ret = ioctl(fd, TIOCMIWAIT, TIOCM_DSR | TIOCM_CTS | TIOCM_CD | TIOCM_RNG);
			if(ret < 0) {
				if(DBG) fprintf(stderr, "%s%d\n", "NATIVE event_looper() failed in ioctl TIOCMIWAIT with error number : -", errno);
				if(DBG) fflush(stderr);
				continue;
			}

#endif
#if defined (__APPLE__)
			usleep(500000); /* 0.5 seconds */
#endif

			/* Something happened on status line so get it. */
			errno = 0;
			ret = ioctl(fd, TIOCMGET, &lines_status);
			if(ret < 0) {
				if(DBG) fprintf(stderr, "%s%d\n", "NATIVE event_looper() failed in ioctl TIOCMGET with error number : -", errno);
				if(DBG) fflush(stderr);
				continue;
			}

			cts = (lines_status & TIOCM_CTS) ? 1 : 0;
			dsr = (lines_status & TIOCM_DSR) ? 1 : 0;
			dcd = (lines_status & TIOCM_CD)  ? 1 : 0;
			ri  = (lines_status & TIOCM_RI)  ? 1 : 0;

			if(cts) {
				event = event | CTS;
			}
			if(dsr) {
				event = event | DSR;
			}
			if(dcd) {
				event = event | DCD;
			}
			if(ri) {
				event = event | RI;
			}

#if defined (__linux__)
			if(DBG) fprintf(stderr, "%s %d\n", "NATIVE event_looper() sending bit mapped events ", event);
			if(DBG) fflush(stderr);
			/* Pass this to java layer inserting event in event queue. */
			(*env)->CallVoidMethod(env, looper, mid, event);
			if((*env)->ExceptionOccurred(env)) {
				LOGE(env);
			}
#endif
#if defined (__APPLE__)
			newstate = event;
			if(newstate != oldstate) {
				if(DBG) fprintf(stderr, "%s %d\n", "NATIVE event_looper() sending bit mapped events ", event);
				if(DBG) fflush(stderr);
				/* Pass this to java layer inserting event in event queue. */
				(*env)->CallVoidMethod(env, looper, mid, event);
				if((*env)->ExceptionOccurred(env)) {
					LOGE(env);
				}
				oldstate = newstate;
			}
#endif
		} /* Go back to loop again waiting for event to happen. */

		return ((void *)0);
	}

	/* This handler is invoked whenever application unregisters port monitor listener.
	 * In future this function may be replaced with some new mechanism. */
	void exitMonitor_signal_handler(int signal_number) {
		int ret = -1;
		if(signal_number == SIGUSR1) {
#if defined (__linux__)
			ret = (*jvm_port)->DetachCurrentThread(jvm_port);
			if(ret != JNI_OK) {
				if(DBG) fprintf(stderr, "%s %d\n", "NATIVE exitMonitor_signal_handler() failed to exit port monitor thread with JNI error ", ret);
				if(DBG) fflush(stderr);
			}
			pthread_exit((void *)0);
#endif
#if defined (__APPLE__)
			int x=0;
			struct driver_ref* ptr;
			pthread_t tid  = pthread_self();
			for (x=0; x < 2048; x++) {
				ptr = pm_info[x];
				if(pm_info[x] == 0) {
					continue;
				}
				if((*ptr->data).thread_id == tid) {
					IOObjectRelease(ptr->notification); /* Remove the driver state change notification.        */
					IOObjectRelease(ptr->service);      /* Release our reference to the driver object.         */
					free(ptr);                          /* Release structure that holds the driver connection. */
				}
				pm_info[x] = 0;
			}

			pthread_exit((void *)0);
#endif
		}
	}

#if defined (__APPLE__)
	/* Callback associated with run loop which will be invoked whenever a device is removed from system.
	 * In order to keep our device removal detection independent from system, we used stat on file path. */
	void device_removed(void *refCon, io_service_t service, natural_t messageType, void *messageArgument) {
		int ret = 0;
		struct stat st;
		struct driver_ref* driver_reference = (struct driver_ref*) refCon;

		if(messageType == kIOMessageServiceIsTerminated) {
			errno = 0;
			ret = stat((*driver_reference->data).port_name, &st);
			if(ret == 0) {
			}else {
				if(errno == EACCES) {
					if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor does not have permission to stat port error : ", errno);
					if(DBG) fflush(stderr);
				}else if(errno == ELOOP) {
					if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor encountered too many symbolic links while traversing the path error : ", errno);
					if(DBG) fflush(stderr);
				}else if(errno == ENAMETOOLONG) {
					if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor path is too long error : ", errno);
					if(DBG) fflush(stderr);
				}else if(errno == ENOMEM) {
					if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor Out of memory (i.e. kernel memory) error : ", errno);
					if(DBG) fflush(stderr);
				}else if(errno == ENOTDIR) {
					if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor a component of the path prefix of path is not a directory error : ", errno);
					if(DBG) fflush(stderr);
				}else if(errno == EOVERFLOW) {
					if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor improper data size handling/definition error : ", errno);
					if(DBG) fflush(stderr);
				}else if(errno == EFAULT) {
					if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor bad address error : ", errno);
					if(DBG) fflush(stderr);
				}else {
					JNIEnv* env = (*driver_reference->data).env;
					jclass port_listener = (*driver_reference->data).port_listener;
					jmethodID mid = (*driver_reference->data).mid;
					(*env)->CallVoidMethod(env, port_listener, mid, 2); /* arg 2 represent device remove action */
					if((*env)->ExceptionOccurred(env)) {
						LOGE(env);
					}
				}
			}
		}
	}

	/* Callback associated with run loop which will be invoked whenever a device is added into system.
	 * When program starts, we manually call this function, and therefore addition of device event
	 * gets sent to application. To prevent this, on very first run we detect value of tempVal
	 * and y pass notification. */
	void device_added(void *refCon, io_iterator_t iterator) {
		io_service_t service = 0;
		kern_return_t kr;
		struct driver_ref dref;

		/* call the application */
		if(((struct port_info*) refCon)->tempVal != 0) {
			JNIEnv* env = ((struct port_info*) refCon)->env;
			jclass port_listener = ((struct port_info*) refCon)->port_listener;
			jmethodID mid = ((struct port_info*) refCon)->mid;
			(*env)->CallVoidMethod(env, port_listener, mid, 1);  /* arg 1 represent device added into system */
			if((*env)->ExceptionOccurred(env)) {
				LOGE(env);
			}
		}else {
			((struct port_info*) refCon)->tempVal = 1;
		}

		/* Iterate over all matching objects. */
		while ((service = IOIteratorNext(iterator)) != 0) {

			/* create arguments to be passed to call back. */
			struct driver_ref *driver_reference = (struct driver_ref *) malloc(sizeof(dref));

			/* pass common global info */
			driver_reference->data = ((struct port_info*) refCon)->data;

			/* Save the io_service_t for this driver instance. */
			driver_reference->service = service;

			/* Install a callback to receive notification of driver state changes. */
			kr = IOServiceAddInterestNotification(((struct port_info*) refCon)->notification_port,
					service,                            /* driver object */
					kIOGeneralInterest,
					device_removed,                     /* callback */
					driver_reference,                   /* refCon passed to device_removed callback */
					&driver_reference->notification);

			/* update global data which will be used when unregistering listener. */
			pm_info[pm_index] = driver_reference;
			pm_index++;
		}
	}

#endif

	/* This thread keep polling for the physical existence of a port/file/device. When port removal is detected, this
	 * informs java listener and exit. We need to ensure that stat() itself does not fail.
	 * It has been assumed that till this thread has initialized, port will not be unplugged from system.
	 * Link against libudev which provides a set of functions for accessing the udev database and querying sysfs. */
	void *port_monitor(void *arg) {
		struct port_info* params = (struct port_info*) arg;
		JavaVM *jvm = (*params).jvm;
		jvm_port = (*params).jvm;
		jobject port_listener = (*params).port_listener;
		void* env1;
		JNIEnv* env;
		jclass portListener;
		jmethodID mid;

#if defined (__linux__)
		struct stat st;
		int ret = 0;
		int fd;
		fd_set fds;
		struct udev *udev;
		struct udev_device *device;
		struct udev_monitor *monitor;
		char action[32];
		const char *SUBSYSTEM_USB = "usb";
		const char *DEVICE_TYPE_USB = "usb_device";
#endif
#if defined (__APPLE__)
		CFDictionaryRef matching_dictionary = NULL;
		io_iterator_t iter = 0;
		CFRunLoopSourceRef run_loop_source;
		kern_return_t kr;
#endif
#if defined (__SunOS)
		/* TODO solaris */
#endif

		if((*jvm)->AttachCurrentThread(jvm, &env1, NULL) != JNI_OK) {
			if(DBG) fprintf(stderr, "%s \n", "NATIVE event_looper() thread failed to attach itself to JVM.");
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor() thread exiting. Please RETRY registering port listener !");
			if(DBG) fflush(stderr);
			/*todo make this robust as data listner wait till register complete and delete globalref*/
			pthread_exit((void *)0);
		}
		env = (JNIEnv*) env1;

		portListener = (*env)->GetObjectClass(env, port_listener);
		if(portListener == NULL) {
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor() thread could not get class of object of type IPortMonitor !");
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor() thread exiting. Please RETRY registering port listener !");
			if(DBG) fflush(stderr);
			pthread_exit((void *)0);
		}

		mid = (*env)->GetMethodID(env, portListener, "onPortMonitorEvent", "(I)V");
		if((*env)->ExceptionOccurred(env)) {
			LOGE(env);
		}
		if(mid == NULL) {
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor() thread failed to retrieve method id of method onPortRemovedEvent !");
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor() thread exiting. Please RETRY registering port listener !");
			if(DBG) fflush(stderr);
			pthread_exit((void *)0);
		}

#if defined (__linux__)
		/* Create the udev object */
		udev = udev_new();
		if(!udev) {
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor() thread failed to create udev object !");
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor() thread exiting. Please RETRY registering port listener !");
			if(DBG) fflush(stderr);
			pthread_exit((void *)0);
		}

		/* Create new udev monitor and connect to a specified event source. Applications should usually not
	   connect directly to the "kernel" events, because the devices might not be usable at that time,
	   before udev has configured them, and created device nodes. Accessing devices at the same time as
	   udev, might result in unpredictable behavior. The "udev" events are sent out after udev has
	   finished its event processing, all rules have been processed, and needed device nodes are created. */
		monitor = udev_monitor_new_from_netlink(udev, "udev");
		if(monitor == NULL) {
			if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor failed to create udev monitor with error ", ret);
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor() thread exiting. Please RETRY registering port listener !");
			if(DBG) fflush(stderr);
			pthread_exit((void *)0);
		}

		/* This filter is efficiently executed inside the kernel, and libudev subscribers will
	   usually not be woken up for devices which do not match. The filter must be installed
	   before the monitor is switched to listening mode. */
		ret = udev_monitor_filter_add_match_subsystem_devtype(monitor, SUBSYSTEM_USB, DEVICE_TYPE_USB);
		if(ret != 0) {
			if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor failed to install filter for udev with error ", ret);
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor() thread exiting. Please RETRY registering port listener !");
			if(DBG) fflush(stderr);
			pthread_exit((void *)0);
		}

		/* Binds the udev_monitor socket to the event source. */
		ret = udev_monitor_enable_receiving(monitor);
		if(ret != 0) {
			if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor failed to bind udev socket to monitor with error ", ret);
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor() thread exiting. Please RETRY registering port listener !");
			if(DBG) fflush(stderr);
			pthread_exit((void *)0);
		}

		/* Retrieve the socket file descriptor associated with the monitor. This fd will get passed to select(). */
		fd = udev_monitor_get_fd(monitor);
		FD_ZERO(&fds);
		FD_SET(fd, &fds);

		/* Install signal handler that will be invoked to indicate that the thread should exit. */
		if(signal(SIGUSR1, exitMonitor_signal_handler) == SIG_ERR) {
			if(DBG) fprintf(stderr, "%s\n", "Unable to create handler for thread exit !");
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor() thread exiting. Please RETRY registering port listener !");
			if(DBG) fflush(stderr);
			pthread_exit((void *)0);
		}

		while(1) {
			ret = select(fd+1, &fds, NULL, NULL, NULL);

			/* Check no error occured, and udev file descriptor indicates event. */
			if((ret > 0) && FD_ISSET(fd, &fds)) {
				device = udev_monitor_receive_device(monitor);
				if(device) {
					memset(action, '\0', sizeof(action));
					strcpy(action, udev_device_get_action(device));
					serial_delay(500);  /* let udev execute udev rules completely (500 milliseconds delay). */
					udev_device_unref(device);

					/* Based on use case and more robust design, notification for plugging port will be
					 * developed in future. As of now we just notify app that some device has been added to system.
					 * In Linux, port name will change, for example at the time of registering this listener
					 * if it was ttyUSB0, then after re-insertion, it will be ttyUSB1. */
					if(strcmp(action, "add") == 0) {
						(*env)->CallVoidMethod(env, port_listener, mid, 1); /* arg 1 represent device add action */
						if((*env)->ExceptionOccurred(env)) {
							LOGE(env);
						}
					}else if(strcmp(action, "remove") == 0) {
						errno = 0;
						ret = stat((*params).port_name, &st);
						if(ret == 0) {
						}else {
							if(errno == EACCES) {
								if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor does not have permission to stat port error : ", errno);
								if(DBG) fflush(stderr);
							}else if(errno == ELOOP) {
								if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor encountered too many symbolic links while traversing the path error : ", errno);
								if(DBG) fflush(stderr);
							}else if(errno == ENAMETOOLONG) {
								if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor path is too long error : ", errno);
								if(DBG) fflush(stderr);
							}else if(errno == ENOMEM) {
								if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor Out of memory (i.e., kernel memory) error : ", errno);
								if(DBG) fflush(stderr);
							}else if(errno == ENOTDIR) {
								if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor a component of the path prefix of path is not a directory error : ", errno);
								if(DBG) fflush(stderr);
							}else if(errno == EOVERFLOW) {
								if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor improper data size handling/definition error : ", errno);
								if(DBG) fflush(stderr);
							}else if(errno == EFAULT) {
								if(DBG) fprintf(stderr, "%s %d\n", "NATIVE port_monitor bad address error : ", errno);
								if(DBG) fflush(stderr);
							}else {
								(*env)->CallVoidMethod(env, port_listener, mid, 2); /* arg 2 represent device remove action */
								if((*env)->ExceptionOccurred(env)) {
									LOGE(env);
								}
							}
						}
					}else {
						/* neither add nor remove action, do nothing. */
					}
				}
			}
		}

		return ((void *)0);
#endif

#if defined (__APPLE__)

		((struct port_info*) arg)->env = env;
		((struct port_info*) arg)->port_listener = port_listener;
		((struct port_info*) arg)->mid = mid;
		((struct port_info*) arg)->tempVal = 0;

		/* Install signal handler that will be invoked to indicate that the thread should exit. */
		if(signal(SIGUSR1, exitMonitor_signal_handler) == SIG_ERR) {
			if(DBG) fprintf(stderr, "%s\n", "Unable to create handler for thread's exit !");
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor() thread exiting. Please RETRY registering port listener !");
			if(DBG) fflush(stderr);
			pthread_exit((void *)0);
		}

		/* Create a matching dictionary that will find any USB device.
		 * Interested in instances of class IOUSBDevice and its subclasses. kIOUSBDeviceClassName */
		matching_dictionary = IOServiceMatching("IOUSBDevice");
		if(matching_dictionary == NULL) {
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor failed to create matching dictionary !");
			if(DBG) fprintf(stderr, "%s \n", "NATIVE port_monitor() thread exiting. Please RETRY registering port listener !");
			if(DBG) fflush(stderr);
			pthread_exit((void *)0);
		}

		/* Create a notification object for receiving IOKit notifications of new devices or state changes. */
		((struct port_info*) arg)->notification_port = IONotificationPortCreate(kIOMasterPortDefault);

		/* CFRunLoopSource to be used to listen for notifications. */
		run_loop_source = IONotificationPortGetRunLoopSource(((struct port_info*) arg)->notification_port);

		/* Adds a CFRunLoopSource object to a run loop mode. */
		CFRunLoopAddSource(CFRunLoopGetCurrent(), run_loop_source, kCFRunLoopDefaultMode);

		/* Look up registered IOService objects that match a matching dictionary, and install a notification request of new IOServices that match.
		 * It associates the matching dictionary with the notification port (and run loop source), allocates and returns an iterator object.
		 * The kIOFirstMatchNotification is delivered when an IOService has had all matching drivers in the kernel probed and started, but only
		 * once per IOService instance. Some IOService's may be re-registered when their state is changed.*/
		kr = IOServiceAddMatchingNotification(((struct port_info*) arg)->notification_port, kIOFirstMatchNotification, matching_dictionary, device_added, ((struct port_info*) arg)->data, &iter);

		/* Iterate once to get already-present devices and arm the notification. */
		device_added(((struct port_info*) arg)->data, iter);

		/* Start the run loop to begin receiving notifications. */
		CFRunLoopRun();

		/* We should never get here. */
		if(DBG) fprintf(stderr, "Unexpectedly returned from CFRunLoopRun(). Something went wrong !\n");
		if(DBG) fflush(stderr);
		return ((void *)0);

#endif

#if defined (__SunOS)
#endif
	}

#endif /* End compiling for Unix-like OS. */

