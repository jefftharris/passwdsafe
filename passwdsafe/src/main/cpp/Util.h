/*
*
* Copyright (c) 2003-2014 Rony Shapiro <ronys@users.sourceforge.net>.
* Copyright (c) 2025 Jeff Harris <jefftharris@gmail.com>
* All rights reserved. Use of the code is allowed under the
* Artistic License 2.0 terms, as specified in the LICENSE file
* distributed with this code, or available from
* http://www.opensource.org/licenses/artistic-license-2.0.php
*/
#ifndef INCLUDE_UTIL_H
#define INCLUDE_UTIL_H

// Util.h
//-----------------------------------------------------------------------------

[[gnu::noinline]] extern void trashMemory(void *buffer, size_t length);
[[gnu::noinline]] extern void burnStack(size_t len); // borrowed from libtomcrypt
#endif /* INCLUDE_UTIL_H */
//-----------------------------------------------------------------------------
