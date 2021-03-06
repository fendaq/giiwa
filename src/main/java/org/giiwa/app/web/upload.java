/*
 * Copyright 2015 JIHU, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.giiwa.app.web;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.giiwa.core.bean.UID;
import org.giiwa.core.bean.X;
import org.giiwa.core.conf.Global;
import org.giiwa.core.json.JSON;
import org.giiwa.framework.bean.*;
import org.giiwa.framework.web.*;

/**
 * web api：/upload <br>
 * used to upload file and return the file id in file repository, it support
 * "resume“ file upload, the "Content-Range: bytes 0-1024/2048"
 * 
 * @author joe
 * 
 */
public class upload extends Model {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.giiwa.framework.web.Model#onPost()
	 */
	@Override
	@Path(login = true)
	public void onPost() {

		JSON jo = new JSON();

		// String access = Module.home.get("upload.require.access");

		FileItem file = this.getFile("file");
		if (file != null) {
			String filename = this.getString("filename");
			if (X.isEmpty(filename)) {
				filename = file.getName();
			}
			store(file, filename, jo);
		} else {
			jo.append(X.STATE, 201).append(X.MESSAGE, lang.get("not [file]"));
		}

		// /**
		// * test
		// */
		// jo.put("error", "error");
		this.response(jo);

	}

	private boolean store(FileItem file, String filename, JSON jo) {
		String tag = this.getString("tag");

		try {
			String range = this.getHeader("Content-Range");
			if (range == null) {
				range = this.getString("Content-Range");
			}
			long position = 0;
			long total = 0;
			String lastModified = this.getHeader("lastModified");
			if (X.isEmpty(lastModified)) {
				lastModified = this.getString("lastModified");
			}
			if (X.isEmpty(lastModified)) {
				lastModified = this.getString("lastModifiedDate");
			}

			if (range != null) {

				// bytes 0-9999/22775650
				String[] ss = range.split(" ");
				if (ss.length > 1) {
					range = ss[1];
				}
				ss = range.split("-|/");
				if (ss.length == 3) {
					position = X.toLong(ss[0]);
					total = X.toLong(ss[2]);
				}

				// log.debug(range + ", " + position + "/" + total);
			}

			String id = UID.id(login.getId(), tag, filename, total, lastModified);

			log.debug("storing, id=" + id + ", name=" + filename + ", tag=" + tag + ", total=" + total + ", last="
					+ lastModified);

			long pos = Repo.append(id, filename, position, total, file.getInputStream(), login.getId(),
					this.getRemoteHost());
			if (pos >= 0) {
				if (jo == null) {
					this.put("url", "/repo/" + id + "/" + filename);
					this.put(X.ERROR, 0);
					this.put("repo", id);
					if (total > 0) {
						this.put("name", filename);
						this.put("pos", pos);
						this.put("size", total);
					}
				} else {
					jo.put("url", "/repo/" + id + "/" + filename);
					jo.put("repo", id);
					jo.put(X.ERROR, 0);
					if (total > 0) {
						jo.put("name", filename);
						jo.put("pos", pos);
						jo.put("size", total);
					}
					jo.put(X.STATE, 200);
					jo.put("site", Global.getString("site.url", X.EMPTY));
				}

				// Session.load(sid()).set("access.repo." + id, 1).store();
			} else {
				if (jo == null) {
					this.set(X.ERROR, HttpServletResponse.SC_BAD_REQUEST);
					this.put(X.MESSAGE, lang.get("repo.locked"));
					this.put(X.STATE, HttpServletResponse.SC_BAD_REQUEST);
				} else {
					jo.put(X.ERROR, HttpServletResponse.SC_BAD_REQUEST);
					jo.put(X.MESSAGE, lang.get("repo.locked"));
					jo.put(X.STATE, HttpServletResponse.SC_BAD_REQUEST);
				}
				return false;
			}
			return true;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			GLog.oplog.error(upload.class, "", e.getMessage(), e, login, this.getRemoteHost());

			if (jo == null) {
				this.set(X.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				this.put(X.MESSAGE, lang.get(e.getMessage()));
				this.put(X.STATE, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			} else {
				jo.put(X.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				jo.put(X.MESSAGE, lang.get(e.getMessage()));
				jo.put(X.STATE, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
		}

		return false;
	}
}
