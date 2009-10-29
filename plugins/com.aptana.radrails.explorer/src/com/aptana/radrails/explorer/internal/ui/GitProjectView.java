package com.aptana.radrails.explorer.internal.ui;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.navigator.CommonNavigator;
import org.eclipse.ui.progress.UIJob;

import com.aptana.git.core.model.ChangedFile;
import com.aptana.git.core.model.GitRepository;
import com.aptana.git.core.model.IGitRepositoryListener;
import com.aptana.git.core.model.IndexChangedEvent;
import com.aptana.git.core.model.RepositoryAddedEvent;
import com.aptana.git.ui.actions.CommitAction;
import com.aptana.git.ui.actions.PullAction;
import com.aptana.git.ui.actions.PushAction;
import com.aptana.git.ui.actions.StashAction;
import com.aptana.radrails.explorer.ExplorerPlugin;

public class GitProjectView extends CommonNavigator implements IGitRepositoryListener
{
	/**
	 * Property we assign to a project to make it the active one that this view is filtered to.
	 */
	private static final String ACTIVE_PROJECT = "activeProject";

	private Combo projectCombo;
	protected IProject selectedProject;
	private Combo branchCombo;
	private Label summary;
	private Button pull;
	private Button push;
	private Button commit;
	private Button stash;

	private ResourceListener fResourceListener;
	private Composite gitStuff;

	@Override
	public void createPartControl(Composite aParent)
	{
		// Create our own parent
		Composite myComposite = new Composite(aParent, SWT.NONE);
		myComposite.setLayout(new FormLayout());

		// Create our special git stuff
		gitStuff = new Composite(myComposite, SWT.NONE);
		gitStuff.setLayout(new GridLayout(2, false));
		FormData gitStuffLayoutData = new FormData();
		gitStuffLayoutData.left = new FormAttachment(0, 0);
		gitStuffLayoutData.top = new FormAttachment(0, 0);
		gitStuff.setLayoutData(gitStuffLayoutData);

		projectCombo = new Combo(gitStuff, SWT.DROP_DOWN | SWT.MULTI | SWT.READ_ONLY);
		GridData projectData = new GridData();
		projectData.horizontalSpan = 2;
		projectCombo.setLayoutData(projectData);
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (IProject iProject : projects)
		{
			projectCombo.add(iProject.getName());
		}

		projectCombo.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				setActiveProject(projectCombo.getText());
			}
		});

		branchCombo = new Combo(gitStuff, SWT.DROP_DOWN | SWT.READ_ONLY);
		branchCombo.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				setNewBranch(branchCombo.getText());
			}
		});

		// Add icon for commit (disk)
		commit = new Button(gitStuff, SWT.FLAT | SWT.PUSH | SWT.CENTER);
		commit.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
		commit.setImage(ExplorerPlugin.getImage("icons/full/elcl16/disk.png"));
		commit.setToolTipText("Commit...");
		commit.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				CommitAction action = new CommitAction();
				ISelection selection = new StructuredSelection(selectedProject);
				action.selectionChanged(null, selection);
				action.run(null);
			}
		});

		summary = new Label(gitStuff, SWT.NONE);
		summary.setText("");
		GridData summaryData = new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false);
		summaryData.verticalSpan = 3;
		summary.setLayoutData(summaryData);

		push = new Button(gitStuff, SWT.FLAT | SWT.PUSH | SWT.CENTER);
		push.setImage(ExplorerPlugin.getImage("icons/full/elcl16/arrow_right.png"));
		push.setToolTipText("Push");
		push.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
		push.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				final PushAction action = new PushAction();
				action.selectionChanged(null, new StructuredSelection(selectedProject));
				Job job = new Job("git push")
				{

					@Override
					protected IStatus run(IProgressMonitor monitor)
					{
						action.run(null);
						refreshUI(GitRepository.getAttached(selectedProject));
						return Status.OK_STATUS;
					}
				};
				job.setUser(true);
				job.setPriority(Job.LONG);
				job.schedule();
			}
		});

		pull = new Button(gitStuff, SWT.FLAT | SWT.PUSH | SWT.CENTER);
		pull.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
		pull.setImage(ExplorerPlugin.getImage("icons/full/elcl16/arrow_left.png"));
		pull.setToolTipText("Pull");
		pull.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				final PullAction action = new PullAction();
				action.selectionChanged(null, new StructuredSelection(selectedProject));
				Job job = new Job("git pull")
				{

					@Override
					protected IStatus run(IProgressMonitor monitor)
					{
						action.run(null);
						return Status.OK_STATUS;
					}
				};
				job.setUser(true);
				job.setPriority(Job.LONG);
				job.schedule();
			}
		});

		stash = new Button(gitStuff, SWT.FLAT | SWT.PUSH | SWT.CENTER);
		stash.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
		stash.setImage(ExplorerPlugin.getImage("icons/full/elcl16/arrow_down.png"));
		stash.setToolTipText("Stash");
		stash.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				final StashAction action = new StashAction();
				action.selectionChanged(null, new StructuredSelection(selectedProject));
				Job job = new Job("git stash")
				{

					@Override
					protected IStatus run(IProgressMonitor monitor)
					{
						action.run(null);
						refreshUI(GitRepository.getAttached(selectedProject));
						return Status.OK_STATUS;
					}
				};
				job.setUser(true);
				job.setPriority(Job.LONG);
				job.schedule();
			}
		});

		// Now create the typical stuff for the navigator
		Composite viewer = new Composite(myComposite, SWT.NONE);
		viewer.setLayout(new FillLayout());
		FormData data2 = new FormData();
		data2.top = new FormAttachment(gitStuff);
		data2.bottom = new FormAttachment(100, 0);
		data2.right = new FormAttachment(100, 0);
		data2.left = new FormAttachment(0, 0);
		viewer.setLayoutData(data2);
		super.createPartControl(viewer);

		fResourceListener = new ResourceListener();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(fResourceListener, IResourceChangeEvent.POST_CHANGE);
		GitRepository.addListener(this);
		if (projects.length > 0)
			detectSelectedProject();
	}

	@Override
	public void dispose()
	{
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(fResourceListener);
		GitRepository.removeListener(this);
		super.dispose();
	}

	protected void reloadProjects()
	{
		Job job = new UIJob("Reload Projects")
		{

			@Override
			public IStatus runInUIThread(IProgressMonitor monitor)
			{
				// FIXME What if the active project was deleted or renamed?
				projectCombo.removeAll();
				IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
				for (IProject iProject : projects)
				{
					projectCombo.add(iProject.getName());
				}
				projectCombo.setText(selectedProject.getName());
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		job.setPriority(Job.INTERACTIVE);
		job.schedule();
	}

	protected void setNewBranch(String branchName)
	{
		if (branchName.endsWith("*"))
			branchName = branchName.substring(0, branchName.length() - 1);

		GitRepository repo = GitRepository.getAttached(selectedProject);
		if (repo != null)
		{
			if (branchName.equals(repo.currentBranch()))
				return;
			repo.switchBranch(branchName);
			refreshViewer();
		}
	}

	private void detectSelectedProject()
	{
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		if (projects == null)
			return;
		for (IProject iProject : projects)
		{
			try
			{
				String value = iProject.getPersistentProperty(new QualifiedName(ExplorerPlugin.PLUGIN_ID,
						ACTIVE_PROJECT));
				if (value != null && value.equals(Boolean.TRUE.toString()))
				{
					projectCombo.setText(iProject.getName());
					setActiveProject(iProject.getName());
					return;
				}
			}
			catch (CoreException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	protected void setActiveProject(String projectName)
	{
		IProject newSelectedProject = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		if (newSelectedProject == null || (selectedProject != null && newSelectedProject.equals(selectedProject)))
			return;
		try
		{
			if (selectedProject != null)
			{
				selectedProject
						.setPersistentProperty(new QualifiedName(ExplorerPlugin.PLUGIN_ID, ACTIVE_PROJECT), null);
			}
			selectedProject = newSelectedProject;
			selectedProject.setPersistentProperty(new QualifiedName(ExplorerPlugin.PLUGIN_ID, ACTIVE_PROJECT),
					Boolean.TRUE.toString());
			refreshUI(GitRepository.getAttached(newSelectedProject));
			// Refresh the view so our filter gets updated!
			refreshViewer();
		}
		catch (CoreException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	private void populateBranches(GitRepository repo)
	{
		branchCombo.removeAll();
		if (repo == null)
			return;
		// FIXME This doesn't seem to indicate proper dirty status and changed files on initial load!
		String currentBranchName = repo.currentBranch();
		for (String branchName : repo.localBranches())
		{
			if (branchName.equals(currentBranchName) && repo.isDirty())
				branchCombo.add(branchName + "*");
			else
				branchCombo.add(branchName);
		}
		if (repo.isDirty())
			currentBranchName += "*";
		branchCombo.setText(currentBranchName);
		branchCombo.pack(true);
	}

	private void refreshViewer()
	{
		if (getCommonViewer() == null)
			return;
		getCommonViewer().refresh();
	}

	public void indexChanged(final IndexChangedEvent e)
	{
		refreshUI(e.getRepository());
	}

	protected void refreshUI(final GitRepository repository)
	{
		Job job = new UIJob("update UI for index changes")
		{

			@Override
			public IStatus runInUIThread(IProgressMonitor monitor)
			{
				if (repository == null)
				{
					push.setEnabled(false);
					pull.setEnabled(false);
					stash.setEnabled(false);
					commit.setEnabled(false);
					push.setVisible(false);
					pull.setVisible(false);
					commit.setVisible(false);
					stash.setVisible(false);
					branchCombo.setVisible(false);
					summary.setVisible(false);
					// FIXME Make gitStuff have a form layout, add composite to hold the buttons/summary/branch and use two form layouts to toggle between showing the details or not!
				}
				else
				{
					// Disable push unless there's a remote tracking branch and we have committed changes
					String[] commitsAhead = repository.commitsAhead(repository.currentBranch());
					push.setEnabled(commitsAhead != null && commitsAhead.length > 0);
					// Disable pull unless there's a remote tracking branch
					pull.setEnabled(repository.trackingRemote(repository.currentBranch()));
					// TODO Disable stash unless we have changes to stash
					stash.setEnabled(true);
					// TODO Disable commit unless there are changes to commit
					commit.setEnabled(true);
					push.setVisible(true);
					pull.setVisible(true);
					commit.setVisible(true);
					stash.setVisible(true);
					summary.setVisible(true);
					branchCombo.setVisible(true);
				}
				// Update the branch list so we can reset the dirty status on the branch
				populateBranches(repository);
				updateSummaryText(repository);
				gitStuff.layout(true);
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		job.setPriority(Job.INTERACTIVE);
		job.schedule();
	}

	private void updateSummaryText(GitRepository repo)
	{
		if (repo == null)
		{
			summary.setText("");
			return;
		}
		int deletedCount = 0;
		int addedCount = 0;
		int modifiedCount = 0;
		if (repo.index().changedFiles() != null)
		{
			for (ChangedFile file : repo.index().changedFiles())
			{
				if (file == null)
					continue;
				if (file.getStatus().equals(ChangedFile.Status.DELETED))
				{
					deletedCount++;
				}
				else if (file.getStatus().equals(ChangedFile.Status.NEW))
				{
					addedCount++;
				}
				else
				{
					modifiedCount++;
				}
			}
		}
		String branch = repo.currentBranch();
		String[] commitsAhead = repo.commitsAhead(branch);
		StringBuilder builder = new StringBuilder();
		if (commitsAhead != null && commitsAhead.length > 0)
		{
			builder.append("Your branch is ahead of '");
			builder.append(repo.remoteTrackingBranch(branch).shortName()).append("' by ");
			builder.append(commitsAhead.length).append(" commit(s)\n");
		}
		builder.append(modifiedCount).append(" file(s) modified\n");
		builder.append(deletedCount).append(" file(s) deleted\n");
		builder.append(addedCount).append(" file(s) added");
		summary.setText(builder.toString());
	}

	public void repositoryAdded(RepositoryAddedEvent e)
	{
		// TODO Someone may have just attached the current project to a repo! We need to update our UI if they did
		GitRepository repo = e.getRepository();
		GitRepository selectedRepo = GitRepository.getAttached(selectedProject);
		if (selectedRepo != null && selectedRepo.equals(repo))
			refreshUI(e.getRepository());
	}

	private class ResourceListener implements IResourceChangeListener
	{

		public void resourceChanged(IResourceChangeEvent event)
		{
			IResourceDelta delta = event.getDelta();
			if (delta == null)
				return;
			try
			{
				delta.accept(new IResourceDeltaVisitor()
				{

					public boolean visit(IResourceDelta delta) throws CoreException
					{
						IResource resource = delta.getResource();
						if (resource.getType() == IResource.FILE || resource.getType() == IResource.FOLDER)
							return false;
						if (resource.getType() == IResource.ROOT)
							return true;
						if (resource.getType() == IResource.PROJECT)
						{
							// a project was added, removed, or changed!
							reloadProjects();
						}
						return false;
					}
				});
			}
			catch (CoreException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
