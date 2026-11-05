(async () => {
  const out = {};
  const run = async (name, fn) => { try { const r = await fn(); out[name] = 'ok ' + JSON.stringify(r === undefined ? null : r).slice(0, 110); } catch (e) { out[name] = 'FAIL ' + String(e && (e.msg || e.message) || e).slice(0, 110); } };
  const user = await import('/src/api/user.ts'), route = await import('/src/api/route.ts'), auth = await import('/src/api/auth.ts'), group = await import('/src/api/imGroup.ts'), feed = await import('/src/api/imFeed.ts');
  const ts = Date.now();
  await run('getUserListApi', () => user.getUserListApi({ pageNum: 1, pageSize: 2 }).then(p => ({ total: p.total, first: p.records[0] && p.records[0].userName !== undefined })));
  await run('addUserApi', () => user.addUserApi({ userName: 'ui' + ts, nickName: 'UI', password: 'abc123' }).then(u => { window.__u = u; return u.id; }));
  await run('getUserDetailApi', () => user.getUserDetailApi(window.__u.id).then(u => u.userName));
  await run('editUserApi', () => user.editUserApi({ id: window.__u.id, nickName: 'UI2' }).then(u => u.nickName));
  await run('resetPasswordApi', () => user.resetPasswordApi({ id: window.__u.id, password: 'abc456' }));
  await run('updateUserStateApi', () => user.updateUserStateApi({ id: window.__u.id, state: false }));
  await run('deleteUserApi', () => user.deleteUserApi({ id: window.__u.id }));
  await run('getCurrentUserInfoApi', () => user.getCurrentUserInfoApi().then(u => u.userName));
  await run('updateCurrentUserApi', () => user.updateCurrentUserApi({ nickName: 'Admin UI' }).then(u => u.nickName));
  await run('uploadAvatarApi', () => user.uploadAvatarApi(new File([new Uint8Array([137, 80, 78, 71])], 'a.png', { type: 'image/png' })).then(r => !!r.url));
  await run('getConstantRoutesApi', () => route.getConstantRoutesApi(1).then(r => Array.isArray(r)));
  await run('getVisibleResourceApi', () => route.getVisibleResourceApi(1).then(r => r.length));
  await run('getResourceTreeApi', () => route.getResourceTreeApi(1).then(r => r.length));
  await run('checkRouteExistApi', () => route.checkRouteExistApi('/user/org'));
  await run('addResourceApi', () => route.addResourceApi({ applicationId: '1', code: 'ui' + ts, name: 'ui' + ts, type: '40', state: true }).then(r => { window.__r = r; return r.resourceType; }));
  await run('editResourceApi', () => route.editResourceApi({ id: window.__r.id, name: 'ui2' + ts }).then(r => r.name === 'ui2' + ts));
  await run('deleteResourceApi', () => route.deleteResourceApi({ id: window.__r.id }));
  await run('updatePasswordApi wrong old', () => auth.updatePasswordApi({ oldPassword: 'nope', newPassword: 'x', confirmPassword: 'x' }));
  await run('deleteImFeedComment none', () => feed.deleteImFeedComment('999999'));
  await run('muteMember bad room', () => group.muteMember({ roomId: '999999', uid: '1', duration: 60 }));
  return JSON.stringify(out, null, 0);
})()
