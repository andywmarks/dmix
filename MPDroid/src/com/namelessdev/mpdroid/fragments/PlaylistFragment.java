package com.namelessdev.mpdroid.fragments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.a0z.mpd.MPDPlaylist;
import org.a0z.mpd.MPDStatus;
import org.a0z.mpd.Music;
import org.a0z.mpd.event.StatusChangeListener;
import org.a0z.mpd.exception.MPDServerException;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.namelessdev.mpdroid.MPDApplication;
import com.namelessdev.mpdroid.R;
import com.namelessdev.mpdroid.library.PlaylistEditActivity;
import com.namelessdev.mpdroid.tools.Tools;
import com.namelessdev.mpdroid.views.TouchInterceptor;

public class PlaylistFragment extends SherlockListFragment implements StatusChangeListener {
	private ArrayList<HashMap<String, Object>> songlist;
	private List<Music> musics;

	private String title;
	private MPDApplication app;
	private ListView list;
	private ActionMode actionMode;

	public static final int MAIN = 0;
	public static final int CLEAR = 1;
	public static final int MANAGER = 3;
	public static final int SAVE = 4;
	public static final int EDIT = 2;

	public PlaylistFragment() {
		super();
		setHasOptionsMenu(true);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		app = (MPDApplication) getActivity().getApplication();
	}

	@SuppressLint("NewApi")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.playlist_activity, container, false);
		list = (ListView) view.findViewById(android.R.id.list);
		((TouchInterceptor) list).setDropListener(dropListener);
		list.setCacheColorHint(getResources().getColor(R.color.nowplaying_background));
		list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
		list.setMultiChoiceModeListener(new MultiChoiceModeListener() {

			@Override
			public boolean onPrepareActionMode(ActionMode mode, android.view.Menu menu) {
				actionMode = mode;
				return false;
			}

			@Override
			public void onDestroyActionMode(ActionMode mode) {
				actionMode = null;
			}

			@Override
			public boolean onCreateActionMode(ActionMode mode, android.view.Menu menu) {
				final android.view.MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.mpd_queuemenu, menu);
				return true;
			}

			@SuppressWarnings("unchecked")
			@Override
			public boolean onActionItemClicked(ActionMode mode, android.view.MenuItem item) {
				switch (item.getItemId()) {
					case R.id.menu_delete:
						final SparseBooleanArray checkedItems = list.getCheckedItemPositions();
						final int count = list.getCount();
						final int positions[] = new int[list.getCheckedItemCount()];
						final ListAdapter adapter = list.getAdapter();
						int j = 0;
						for (int i = 0; i < count; i++) {
							if (checkedItems.get(i)) {
								positions[j] = ((Integer) ((HashMap<String, Object>) adapter.getItem(i)).get("songid")).intValue();
								j++;
							}
						}
						app.oMPDAsyncHelper.execAsync(new Runnable() {
							@Override
							public void run() {
								try {
									app.oMPDAsyncHelper.oMPD.getPlaylist().removeById(positions);
								} catch (MPDServerException e) {
									e.printStackTrace();
								}
							}
						});

						mode.finish();
						return true;
					default:
						return false;
				}
			}

			@Override
			public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
				final int selectCount = list.getCheckedItemCount();
				if (selectCount == 0)
					mode.finish();
				if (selectCount == 1) {
					mode.setTitle(R.string.actionSongSelected);
				} else {
					mode.setTitle(getString(R.string.actionSongsSelected, selectCount));
				}
			}
		});

		return view;
	}

	protected void update() {
		try {
			MPDPlaylist playlist = app.oMPDAsyncHelper.oMPD.getPlaylist();
			songlist = new ArrayList<HashMap<String, Object>>();
			musics = playlist.getMusicList();
			int playingID = app.oMPDAsyncHelper.oMPD.getStatus().getSongId();
			// The position in the songlist of the currently played song
			int listPlayingID = -1;
			for (Music m : musics) {
				if (m == null) {
					continue;
				}
				HashMap<String, Object> item = new HashMap<String, Object>();
				item.put("songid", m.getSongId());
				if (Tools.isStringEmptyOrNull(m.getAlbum())) {
					item.put("artist", m.getArtist());
				} else {
					item.put("artist", m.getArtist() + " - " + m.getAlbum());
				}
				item.put("title", m.getTitle());
				if (m.getSongId() == playingID) {
					item.put("play", android.R.drawable.ic_media_play);
					// Lie a little. Scroll to the previous song than the one playing. That way it shows that there are other songs before
					// it
					listPlayingID = songlist.size() - 1;
				} else {
					item.put("play", 0);
				}
				songlist.add(item);
			}

			final int finalListPlayingID = listPlayingID;

			getActivity().runOnUiThread(new Runnable() {
				@TargetApi(Build.VERSION_CODES.HONEYCOMB)
				public void run() {
					SimpleAdapter songs = new SimpleAdapter(getActivity(), songlist, R.layout.playlist_queue_item, new String[] {
							"play",
							"title", "artist" }, new int[] { R.id.picture, android.R.id.text1, android.R.id.text2 });

					setListAdapter(songs);
					if (actionMode != null)
						actionMode.finish();

					// Only scroll if there is a valid song to scroll to. 0 is a valid song but does not require scroll anyway.
					// Also, only scroll if it's the first update. You don't want your playlist to scroll itself while you are looking at
					// other
					// stuff.
					if (finalListPlayingID > 0)
						setSelection(finalListPlayingID);
				}
			});

		} catch (MPDServerException e) {
		}

	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onResume() {
		super.onResume();
		app.oMPDAsyncHelper.addStatusChangeListener(this);
		new Thread(new Runnable() {
			public void run() {
				update();
			}
		}).start();
	}

	@Override
	public void onPause() {
		app.oMPDAsyncHelper.removeStatusChangeListener(this);
		super.onPause();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

		super.onCreateContextMenu(menu, v, menuInfo);
		android.view.MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(R.menu.mpd_playlistcnxmenu, menu);

		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		// arrayListId = info.position;

		title = (String) songlist.get(info.position).get("title");
		menu.setHeaderTitle(title);
	}

	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		MPDApplication app = (MPDApplication) getActivity().getApplication();
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		int songId = (Integer) songlist.get(info.position).get("songid");
		switch (item.getItemId()) {
			case R.id.PLCX_SkipToHere:
				// skip to selected Song
				try {
					app.oMPDAsyncHelper.oMPD.skipToId(songId);
				} catch (MPDServerException e) {
				}
				return true;
			case R.id.PLCX_playNext:
				try { // Move song to next in playlist
					MPDStatus status = app.oMPDAsyncHelper.oMPD.getStatus();
					if (info.id < status.getSongPos()) {
						app.oMPDAsyncHelper.oMPD.getPlaylist().move(songId, status.getSongPos());
					} else {
						app.oMPDAsyncHelper.oMPD.getPlaylist().move(songId, status.getSongPos() + 1);
					}
					Tools.notifyUser("Song moved to next in list", getActivity());
				} catch (MPDServerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return true;
			case R.id.PLCX_moveFirst:
				try { // Move song to first in playlist
					app.oMPDAsyncHelper.oMPD.getPlaylist().move(songId, 0);
					Tools.notifyUser("Song moved to first in list", getActivity());
				} catch (MPDServerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return true;
			case R.id.PLCX_moveLast:
				try { // Move song to last in playlist
					MPDStatus status = app.oMPDAsyncHelper.oMPD.getStatus();
					app.oMPDAsyncHelper.oMPD.getPlaylist().move(songId, status.getPlaylistLength() - 1);
					Tools.notifyUser("Song moved to last in list", getActivity());
				} catch (MPDServerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return true;
			case R.id.PLCX_removeFromPlaylist:
				try {
					app.oMPDAsyncHelper.oMPD.getPlaylist().removeById(songId);
					Tools.notifyUser(getResources().getString(R.string.deletedSongFromPlaylist), getActivity());
				} catch (MPDServerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return true;
			default:
				return super.onContextItemSelected(item);
		}
	}

	/*
	 * Create Menu for Playlist View
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.mpd_playlistmenu, menu);
		menu.removeItem(R.id.PLM_EditPL);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Menu actions...
		Intent i;
		switch (item.getItemId()) {
			case R.id.PLM_Clear:
				try {
					app.oMPDAsyncHelper.oMPD.getPlaylist().clear();
					songlist.clear();
					Tools.notifyUser(getResources().getString(R.string.playlistCleared), getActivity());
					((SimpleAdapter) getListAdapter()).notifyDataSetChanged();
				} catch (MPDServerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return true;
			case R.id.PLM_EditPL:
				i = new Intent(getActivity(), PlaylistEditActivity.class);
				startActivity(i);
				return true;
			case R.id.PLM_Save:
				final EditText input = new EditText(getActivity());
				new AlertDialog.Builder(getActivity())
						.setTitle(R.string.playlistName)
						.setMessage(R.string.newPlaylistPrompt)
						.setView(input)
						.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								final String name = input.getText().toString().trim();
								if (null != name && name.length() > 0) {
									app.oMPDAsyncHelper.execAsync(new Runnable() {
										@Override
										public void run() {
											try {
												app.oMPDAsyncHelper.oMPD.getPlaylist().savePlaylist(name);
											} catch (MPDServerException e) {
												e.printStackTrace();
											}
										}
									});
								}
							}
						}).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								// Do nothing.
							}
						}).show();
				return true;
			default:
				return false;
		}

	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {

		MPDApplication app = (MPDApplication) getActivity().getApplication(); // Play selected Song

		Music m = musics.get(position);
		try {
			app.oMPDAsyncHelper.oMPD.skipToId(m.getSongId());
		} catch (MPDServerException e) {
		}

	}

	public void scrollToNowPlaying() {
		for (HashMap<String, Object> song : songlist) {
			try {
				if (((Integer) song.get("songid")).intValue() == ((MPDApplication) getActivity().getApplication()).oMPDAsyncHelper.oMPD
						.getStatus()
						.getSongId()) {
					getListView().requestFocusFromTouch();
					getListView().setSelection(songlist.indexOf(song));
				}
			} catch (MPDServerException e) {
			}
		}
	}

	@Override
	public void volumeChanged(MPDStatus mpdStatus, int oldVolume) {
		// TODO Auto-generated method stub

	}

	@Override
	public void playlistChanged(MPDStatus mpdStatus, int oldPlaylistVersion) {
		update();

	}

	@Override
	public void trackChanged(MPDStatus mpdStatus, int oldTrack) {
		// Mark running track...
		for (HashMap<String, Object> song : songlist) {
			if (((Integer) song.get("songid")).intValue() == mpdStatus.getSongId())
				song.put("play", android.R.drawable.ic_media_play);
			else
				song.put("play", 0);

		}
		final SimpleAdapter adapter = (SimpleAdapter) getListAdapter();
		if (adapter != null)
			adapter.notifyDataSetChanged();

	}

	@Override
	public void stateChanged(MPDStatus mpdStatus, String oldState) {
		// TODO Auto-generated method stub

	}

	@Override
	public void repeatChanged(boolean repeating) {
		// TODO Auto-generated method stub

	}

	@Override
	public void randomChanged(boolean random) {
		// TODO Auto-generated method stub

	}

	@Override
	public void connectionStateChanged(boolean connected, boolean connectionLost) {
		// TODO Auto-generated method stub

	}

	@Override
	public void libraryStateChanged(boolean updating) {
		// TODO Auto-generated method stub

	}

	private TouchInterceptor.DropListener dropListener = new TouchInterceptor.DropListener() {
		public void drop(int from, int to) {
			if (from == to) {
				return;
			}
			HashMap<String, Object> itemFrom = songlist.get(from);
			Integer songID = (Integer) itemFrom.get("songid");
			try {
				app.oMPDAsyncHelper.oMPD.getPlaylist().move(songID, to);
			} catch (MPDServerException e) {
			}
		}
	};

}
