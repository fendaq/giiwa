package org.giiwa.app.web.admin;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.giiwa.core.bean.X;
import org.giiwa.app.task.DiskHeartbeat;
import org.giiwa.core.base.IOUtil;
import org.giiwa.core.bean.Beans;
import org.giiwa.core.bean.Helper.V;
import org.giiwa.core.bean.Helper.W;
import org.giiwa.core.dfile.DFile;
import org.giiwa.core.json.JSON;
import org.giiwa.core.task.Task;
import org.giiwa.framework.bean.Disk;
import org.giiwa.framework.bean.Node;
import org.giiwa.framework.bean.Repo;
import org.giiwa.framework.web.Model;
import org.giiwa.framework.web.Path;

public class dfile extends Model {

	@Path(path = "file/delete", login = true, access = "access.config.admin")
	public void file_delete() {

		String f = this.getString("f");
		if (!X.isEmpty(f)) {
			Disk.delete(f);
		}

		this.response(JSON.create().append(X.MESSAGE, 200).append(X.MESSAGE, "删除成功！"));
	}

	@Path(path = "disk", login = true, access = "access.config.admin")
	public void disk() {

		int s = this.getInt("s");
		int n = this.getInt("n", 10);

		W q = W.create().sort("priority", -1).sort("path", 1);
		String name = this.getString("name");
		if (!X.isEmpty(name)) {

			W q1 = W.create();
			List<String> l1 = X.toString(Node.dao.distinct("id", W.create()
					.and("updated", System.currentTimeMillis() - Node.LOST, W.OP.gte).and("ip", name, W.OP.like)));
			if (l1 == null || l1.isEmpty()) {
				q1.or("node", X.EMPTY);
			} else {
				q1.or("node", l1);
			}
			q1.or("path", name);
			q.and(q1);

			this.set("name", name);
		}

		Beans<Disk> bs = Disk.dao.load(q, s, n);
		bs.count();

		this.set(bs, s, n);

		this.query.path("/admin/dfile/disk");

		this.show("/admin/dfile.disk.html");

	}

	@Path(path = "disk/add", login = true, access = "access.config.admin")
	public void disk_add() {

		if (method.isPost()) {

			V v = V.create();
			String s = this.getString("s");
			File f = new File(s);

			try {
				v.append("path", f.getCanonicalPath());
				v.append("priority", this.getInt("priority"));
				v.append("node", this.getString("node"));

				Disk.create(v);
				this.response(JSON.create().append(X.STATE, 200));

				DiskHeartbeat.inst.schedule(0);
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				this.response(JSON.create().append(X.STATE, 201).append(X.MESSAGE, e.getMessage()));
			}
			return;
		}

		this.set("nodes",
				Node.dao.load(W.create().and("updated", System.currentTimeMillis() - Node.LOST, W.OP.gte), 0, 10000));

		this.show("/admin/dfile.disk.add.html");

	}

	@Path(path = "file/add", login = true, access = "access.config.admin")
	public void file_add() {

		String f = this.getString("f");
		String repo = this.getString("repo");
		Repo.Entity e = Repo.load(repo);
		if (e != null) {
			try {
				String name = f != null ? (f + "/" + e.getName()) : ("/" + e.getName());

				DFile f1 = Disk.seek(name);
				f1.delete();
				IOUtil.copy(e.getInputStream(), f1.getOutputStream());

				this.response(JSON.create().append(X.STATE, 200).append(X.MESSAGE, "ok"));
				return;
			} catch (Exception e1) {
				log.error(e1.getMessage(), e1);
			} finally {
				e.delete();
			}
		}
		this.response(JSON.create().append(X.STATE, 201).append(X.MESSAGE, "failed"));

	}

	@Path(path = "disk/edit", login = true, access = "access.config.admin")
	public void disk_edit() {

		long id = this.getLong("id");

		if (method.isPost()) {
			V v = V.create();
			v.append("priority", this.getInt("priority"));

			Disk.dao.update(id, v);
			this.response(JSON.create().append(X.STATE, 200));

			DiskHeartbeat.inst.schedule(0);

			return;
		}

		Disk s = Disk.dao.load(id);
		this.set("s", s);
		this.show("/admin/dfile.disk.edit.html");

	}

	@Path(path = "disk/delete", login = true, access = "access.config.admin")
	public void disk_delete() {

		final long id = this.getLong("id");

		final Disk d = Disk.dao.load(id);

		if (d != null) {

			new Task() {
				/**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				@Override
				public boolean interruptable() {
					return Boolean.FALSE;
				}

				@Override
				public void onExecute() {

					try {
						Disk.dao.delete(id);

						DFile f = DFile.create(d, "/");
						DFile[] ff = f.listFiles();
						if (ff != null) {
							for (DFile f1 : ff) {
								copy(d.getPath(), f1);
							}
						}

						DiskHeartbeat.inst.schedule(0);

					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}

				void copy(String pre, DFile f) {
					try {
						if (f.isFile()) {
							try {
								String filename = f.getCanonicalPath().replace(pre, "");
								Disk.copy(f, filename);
							} catch (Exception e) {
								log.error(e.getMessage(), e);
							}
						} else if (f.isDirectory()) {
							DFile[] ff = f.listFiles();
							if (ff != null) {
								for (DFile f1 : ff) {
									copy(pre, f1);
								}
							}
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}

			}.schedule(0);
		}
		this.response(JSON.create().append(X.STATE, 200));
	}

	@Path(path = "folder", login = true, access = "access.config.admin")
	public void folder() {

		String p = this.getString("f");
		if (X.isEmpty(p)) {
			p = "/";
		}

		if (!X.isSame(p, "/")) {
			this.set("f", Disk.seek(p));
		}

		Collection<DFile> list = Disk.list(p);
		this.set("list", list);

		this.query.path("/admin/dfile/folder");

		this.show("/admin/dfile.folder.html");
	}

}
