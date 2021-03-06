package hale.bc.server.web;

import hale.bc.server.repository.MockerDao;
import hale.bc.server.repository.exception.DuplicatedEntryException;
import hale.bc.server.service.UserOperationService;
import hale.bc.server.to.Mocker;
import hale.bc.server.to.MockerType;
import hale.bc.server.to.UserOperation;
import hale.bc.server.to.UserOperationType;
import hale.bc.server.to.result.FailedResult;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/mockers")
public class MockerController {
	
	@Autowired
	private MockerDao mockerDao;
	
	@Autowired
	private UserOperationService userOperationService;
	
	@RequestMapping(method=RequestMethod.POST)
    public Mocker add(@RequestBody Mocker mocker, Principal principal) throws DuplicatedEntryException {
		mocker.setOwner(principal.getName());
		Mocker nm = mockerDao.createMocker(mocker);
		if (nm != null) {
			userOperationService.log(UserOperation.mockerOperation(principal.getName(), mocker, nm, 
					UserOperationType.CreateMocker));
		}
		return nm;
    }
	
	@RequestMapping(value = "/{mid}", method=RequestMethod.GET)
    public Mocker getById(@PathVariable Long mid, Principal principal) {
		Mocker m = mockerDao.getMockerById(mid, principal.getName());
		return m;
    }
	
	@RequestMapping(method=RequestMethod.GET)
    public List<Mocker> getByOwner(Principal principal) {
		List<Mocker> ms = mockerDao.getMockers(principal.getName());
		return ms;
    }
	
	@RequestMapping(value = "/{mid}", method=RequestMethod.PUT)
    public Mocker update(@RequestBody Mocker mocker, 
    			@RequestParam(value = "op", required = true) String operation, 
    			Principal principal)  throws DuplicatedEntryException {

		Mocker m = mockerDao.getMockerById(mocker.getId(), principal.getName());
		if (m == null) {
			return null;
		}
		Mocker nm = mockerDao.updateMocker(mocker);
		if (nm != null) {
			userOperationService.log(UserOperation.mockerOperation(principal.getName(), m, nm, 
					UserOperationType.valueOf(operation)));
		}
		return nm;
    }
	
	@RequestMapping(value = "/{mid}", method=RequestMethod.DELETE)
    public Mocker delete(@PathVariable Long mid, Principal principal) {
		Mocker m = mockerDao.getMockerById(mid, principal.getName());
		if (m == null) {
			return null;
		}
		Mocker nm =  mockerDao.deleteMocker(mid);
		if (nm != null) {
			userOperationService.log(UserOperation.mockerOperation(principal.getName(), m, nm, 
					UserOperationType.DeleteMocker));
		}
		return nm;
    }
	
	@RequestMapping(method=RequestMethod.GET, params="public")
    public List<Mocker> getByPublicType() {
		List<Mocker> ms = mockerDao.getPublicMockers();
		return ms;
    }
	
	@RequestMapping(method=RequestMethod.GET, params="watched")
    public List<Mocker> getWatched(Principal principal) {
		List<Mocker> ms = mockerDao.getMockersByWatcher(principal.getName());
		return ms;
    }
	
	@RequestMapping(value = "/{mid}/watch", method=RequestMethod.PUT)
    public Mocker watch(@PathVariable Long mid, Principal principal) throws DuplicatedEntryException {
		Mocker m = mockerDao.getMockerById(mid);
		if (m == null || m.getType() == MockerType.Private) {
			return null;
		}
		Mocker mocker = null;
		if (mockerDao.putMockerToWatcherGroup(m, principal.getName())) {
			m.increaseWatcherCount();
			mocker = mockerDao.updateMocker(m, false);
			userOperationService.log(UserOperation.mockerOperation(principal.getName(), m, mocker, 
					UserOperationType.WatchMocker));
		}
		return mocker;
    }
	
	@RequestMapping(value = "/{mid}/unwatch", method=RequestMethod.PUT)
    public Mocker cancelCollect(@PathVariable Long mid, Principal principal) throws DuplicatedEntryException {
		Mocker m = mockerDao.getMockerById(mid);
		if (m == null) {
			return null;
		}
		Mocker mocker = null;
		if (mockerDao.removeMockerFromWatcherGroup(m, principal.getName())) {
			m.decreaseWatcherCount();
			mocker = mockerDao.updateMocker(m, false);
			userOperationService.log(UserOperation.mockerOperation(principal.getName(), m, mocker, 
					UserOperationType.UnwatchMocker));
		}
		return mocker;
    }
	
	@ExceptionHandler(DuplicatedEntryException.class)
	public FailedResult handleCustomException(DuplicatedEntryException ex) {
		return new FailedResult(-1, ex.getMessage());
	}
	
}
